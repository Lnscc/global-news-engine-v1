package com.example.globalnewsenginev1.ingestion;

import com.sun.net.httpserver.HttpServer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class GdeltRawImporterPostgresIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("gne")
            .withUsername("gne")
            .withPassword("gne");

    private HttpServer server;
    private JdbcTemplate jdbcTemplate;
    private GdeltRawImporter importer;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = dataSource();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
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
        server.stop(0);
    }

    @Test
    void importsRawGdeltWindowIntoPostgresAndSkipsDuplicateRun() {
        Instant timestamp = Instant.parse("2026-06-05T12:00:00Z");

        List<GdeltImportResult> firstImport = importer.importWindow(timestamp);
        List<GdeltImportResult> secondImport = importer.importWindow(timestamp);

        assertThat(firstImport).allMatch(result -> result.rowCount() == 2 && !result.skipped());
        assertThat(secondImport).allMatch(result -> result.rowCount() == 2 && result.skipped());
        assertThat(countRows("gdelt_raw_events")).isEqualTo(2);
        assertThat(countRows("gdelt_raw_mentions")).isEqualTo(2);
        assertThat(countRows("gdelt_raw_gkg")).isEqualTo(2);
        assertThat(countRows("gdelt_import_files")).isEqualTo(3);
        assertThat(countRows("flyway_schema_history")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM gdelt_import_files
                WHERE status = 'COMPLETED' AND checksum_sha256 IS NOT NULL
                """, Integer.class)).isEqualTo(3);
    }

    private DataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
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
