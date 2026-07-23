package com.example.globalnewsenginev1.gdelt.raw;

import com.sun.net.httpserver.HttpServer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class GdeltRawImporterPostgresIT {

    private HttpServer server;
    private JdbcTemplate jdbcTemplate;
    private GdeltRawImporter importer;
    private DataSource adminDataSource;
    private String schemaName;

    @BeforeEach
    void setUp() throws Exception {
        adminDataSource = adminDataSource();
        Assumptions.assumeTrue(canConnect(adminDataSource),
                "PostgreSQL integration test requires local compose database at jdbc:postgresql://localhost:5432/gne");

        schemaName = "it_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(adminDataSource).execute("CREATE SCHEMA " + schemaName);

        DataSource dataSource = schemaDataSource(schemaName);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .load()
                .migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);

        server = HttpServer.create(new InetSocketAddress(0), 0);
        byte[] zip = zipWithLines("first\trow", "second\trow");
        server.createContext("/gdeltv2/", exchange -> {
            exchange.sendResponseHeaders(200, zip.length);
            exchange.getResponseBody().write(zip);
            exchange.close();
        });
        server.start();

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        URI baseUri = URI.create("http://localhost:" + server.getAddress().getPort() + "/gdeltv2");
        importer = new GdeltRawImporter(jdbcTemplate, transactionTemplate, HttpClient.newHttpClient(), baseUri);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (adminDataSource != null && schemaName != null) {
            new JdbcTemplate(adminDataSource).execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
        }
    }

    @Test
    void importsRawGdeltWindowIntoPostgresAndSkipsDuplicateRun() {
        Instant timestamp = Instant.parse("2026-06-05T12:00:00Z");

        List<GdeltImportResult> firstImport = importer.importWindow(timestamp);
        List<GdeltImportResult> secondImport = importer.importWindow(timestamp);

        assertThat(firstImport).allMatch(result -> result.rowCount() == 2 && !result.skipped());
        assertThat(secondImport).allMatch(result -> result.rowCount() == 2 && result.skipped());
        assertThat(countRows("gdelt_event_payloads")).isEqualTo(2);
        assertThat(countRows("gdelt_mention_payloads")).isEqualTo(2);
        assertThat(countRows("gdelt_gkg_payloads")).isEqualTo(2);
        assertThat(countRows("gdelt_import_files")).isEqualTo(3);
        assertThat(countRows("flyway_schema_history")).isEqualTo(23);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM gdelt_import_files
                WHERE status = 'COMPLETED' AND checksum_sha256 IS NOT NULL
                """, Integer.class)).isEqualTo(3);
    }

    private DataSource adminDataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(System.getProperty("it.postgres.jdbc-url", "jdbc:postgresql://localhost:5432/gne"));
        dataSource.setUser(System.getProperty("it.postgres.username", "gne"));
        dataSource.setPassword(System.getProperty("it.postgres.password", "gne"));
        return dataSource;
    }

    private DataSource schemaDataSource(String schema) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(withCurrentSchema(
                System.getProperty("it.postgres.jdbc-url", "jdbc:postgresql://localhost:5432/gne"),
                schema));
        dataSource.setUser(System.getProperty("it.postgres.username", "gne"));
        dataSource.setPassword(System.getProperty("it.postgres.password", "gne"));
        return dataSource;
    }

    private String withCurrentSchema(String jdbcUrl, String schema) {
        String separator = jdbcUrl.contains("?") ? "&" : "?";
        return jdbcUrl + separator + "currentSchema=" + schema;
    }

    private boolean canConnect(DataSource dataSource) {
        try (Connection ignored = dataSource.getConnection()) {
            return true;
        } catch (SQLException exception) {
            return false;
        }
    }

    private int countRows(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }

    private byte[] zipWithLines(String... lines) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("data.tsv"));
            for (String line : lines) {
                zip.write((line + "\n").getBytes());
            }
        }
        return bytes.toByteArray();
    }
}
