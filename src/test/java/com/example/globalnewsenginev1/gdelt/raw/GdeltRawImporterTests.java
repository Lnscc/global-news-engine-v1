package com.example.globalnewsenginev1.gdelt.raw;

import com.sun.net.httpserver.HttpServer;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class GdeltRawImporterTests {

    private HttpServer server;
    private JdbcTemplate jdbcTemplate;
    private GdeltRawImporter importer;
    private final List<String> requestedPaths = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:gdelt-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V1__create_gdelt_raw_tables.sql"));
            connection.createStatement().execute(
                    "ALTER TABLE gdelt_raw_events RENAME TO gdelt_event_payloads");
            connection.createStatement().execute(
                    "ALTER TABLE gdelt_raw_mentions RENAME TO gdelt_mention_payloads");
        }
        jdbcTemplate = new JdbcTemplate(dataSource);

        server = HttpServer.create(new InetSocketAddress(0), 0);
        byte[] zip = zipWithLines("first\trow", "second\trow");
        server.createContext("/gdeltv2/", exchange -> {
            requestedPaths.add(exchange.getRequestURI().getPath());
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
    void importsAllDatasetsAndSkipsAnAlreadyCompletedWindow() {
        Instant timestamp = Instant.parse("2026-05-31T12:00:00Z");

        List<GdeltImportResult> firstImport = importer.importWindow(timestamp);
        List<GdeltImportResult> secondImport = importer.importWindow(timestamp);

        assertThat(firstImport).allMatch(result -> result.rowCount() == 2 && !result.skipped());
        assertThat(secondImport).allMatch(result -> result.rowCount() == 2 && result.skipped());
        assertThat(countRows("gdelt_event_payloads")).isEqualTo(2);
        assertThat(countRows("gdelt_mention_payloads")).isEqualTo(2);
        assertThat(countRows("gdelt_raw_gkg")).isEqualTo(2);
        assertThat(countRows("gdelt_import_files")).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gdelt_import_files WHERE status = 'COMPLETED' AND checksum_sha256 IS NOT NULL",
                Integer.class)).isEqualTo(3);
        assertThat(requestedPaths).containsExactly(
                "/gdeltv2/20260531120000.export.CSV.zip",
                "/gdeltv2/20260531120000.mentions.CSV.zip",
                "/gdeltv2/20260531120000.gkg.csv.zip");
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
