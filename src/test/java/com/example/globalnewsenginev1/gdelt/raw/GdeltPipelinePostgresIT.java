package com.example.globalnewsenginev1.gdelt.raw;

import com.example.globalnewsenginev1.articles.extraction.ArticleExtractorService;
import com.example.globalnewsenginev1.articles.normalization.ArticleUrlNormalizer;
import com.example.globalnewsenginev1.gdelt.staging.GdeltRawToStagingTransformer;
import com.example.globalnewsenginev1.gdelt.staging.parser.GdeltParserTests;
import com.sun.net.httpserver.HttpServer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class GdeltPipelinePostgresIT {

    private HttpServer server;
    private DataSource adminDataSource;
    private String schemaName;
    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private TransactionTemplate transactionTemplate;
    private GdeltRawImporter importer;

    @BeforeEach
    void setUp() throws Exception {
        adminDataSource = adminDataSource();
        Assumptions.assumeTrue(canConnect(adminDataSource),
                "PostgreSQL integration test requires local compose database at jdbc:postgresql://localhost:5432/gne");

        schemaName = "it_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(adminDataSource).execute("CREATE SCHEMA " + schemaName);
        dataSource = schemaDataSource(schemaName);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .load()
                .migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        Map<String, byte[]> downloads = Map.of(
                "export.CSV.zip", zipWithLine(GdeltParserTests.eventRow()),
                "mentions.CSV.zip", zipWithLine(GdeltParserTests.mentionRow()),
                "gkg.csv.zip", zipWithLine(GdeltParserTests.gkgRow()));
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/gdeltv2/", exchange -> {
            byte[] zip = downloads.entrySet().stream()
                    .filter(entry -> exchange.getRequestURI().getPath().endsWith(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElseThrow();
            exchange.sendResponseHeaders(200, zip.length);
            exchange.getResponseBody().write(zip);
            exchange.close();
        });
        server.start();

        URI baseUri = URI.create("http://localhost:" + server.getAddress().getPort() + "/gdeltv2");
        importer = new GdeltRawImporter(
                jdbcTemplate, transactionTemplate, HttpClient.newHttpClient(), baseUri);
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
    void downloadsParsesNormalizesAndExtractsAllDatasetsIdempotentlyAcrossRestart() {
        Instant timestamp = Instant.parse("2026-07-05T12:00:00Z");

        assertThat(importer.importWindow(timestamp)).allMatch(result -> !result.skipped());
        assertThat(pipelineHealth()).allSatisfy(row -> {
            assertThat(number(row, "payload_rows")).isEqualTo(1);
            assertThat(number(row, "pending_payload_rows")).isEqualTo(1);
            assertThat(number(row, "open_processing_errors")).isZero();
            assertThat(number(row, "domain_rows")).isZero();
        });

        GdeltRawToStagingTransformer transformer =
                new GdeltRawToStagingTransformer(jdbcTemplate, transactionTemplate, java.time.Duration.ZERO);
        ArticleExtractorService extractor = new ArticleExtractorService(
                jdbcTemplate, transactionTemplate, new ArticleUrlNormalizer());

        var transformed = transformer.transformCompletedRawRows(100);
        assertThat(transformed.eventsStaged()).isEqualTo(1);
        assertThat(transformed.mentionsStaged()).isEqualTo(1);
        assertThat(transformed.gkgStaged()).isEqualTo(1);
        assertThat(transformed.errors()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT cardinality(themes) FROM gdelt_gkg", Integer.class)).isGreaterThan(0);
        assertThat(extractor.extractArticles(100).signalsCreated()).isEqualTo(3);

        GdeltRawToStagingTransformer restartedTransformer =
                new GdeltRawToStagingTransformer(jdbcTemplate, transactionTemplate, java.time.Duration.ZERO);
        ArticleExtractorService restartedExtractor = new ArticleExtractorService(
                jdbcTemplate, transactionTemplate, new ArticleUrlNormalizer());

        assertThat(importer.importWindow(timestamp)).allMatch(result -> result.skipped());
        var restartedTransformation = restartedTransformer.transformCompletedRawRows(100);
        assertThat(restartedTransformation.totalStaged()).isZero();
        assertThat(restartedTransformation.errors()).isZero();
        assertThat(restartedExtractor.extractArticles(100).signalsCreated()).isZero();

        assertThat(pipelineHealth()).allSatisfy(row -> {
            assertThat(number(row, "payload_rows")).isEqualTo(1);
            assertThat(number(row, "pending_payload_rows")).isZero();
            assertThat(number(row, "open_processing_errors")).isZero();
            assertThat(number(row, "domain_rows")).isEqualTo(1);
        });
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM articles", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gdelt_events WHERE article_id IS NOT NULL", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gdelt_mentions WHERE article_id IS NOT NULL", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gdelt_gkg WHERE article_id IS NOT NULL", Integer.class)).isEqualTo(1);
    }

    private java.util.List<Map<String, Object>> pipelineHealth() {
        return jdbcTemplate.queryForList("SELECT * FROM gdelt_pipeline_health_view ORDER BY dataset_type");
    }

    private long number(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).longValue();
    }

    private DataSource adminDataSource() {
        PGSimpleDataSource postgres = new PGSimpleDataSource();
        postgres.setUrl(System.getProperty("it.postgres.jdbc-url", "jdbc:postgresql://localhost:5432/gne"));
        postgres.setUser(System.getProperty("it.postgres.username", "gne"));
        postgres.setPassword(System.getProperty("it.postgres.password", "gne"));
        return postgres;
    }

    private DataSource schemaDataSource(String schema) {
        PGSimpleDataSource postgres = new PGSimpleDataSource();
        String jdbcUrl = System.getProperty("it.postgres.jdbc-url", "jdbc:postgresql://localhost:5432/gne");
        postgres.setUrl(jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema);
        postgres.setUser(System.getProperty("it.postgres.username", "gne"));
        postgres.setPassword(System.getProperty("it.postgres.password", "gne"));
        return postgres;
    }

    private boolean canConnect(DataSource source) {
        try (Connection ignored = source.getConnection()) {
            return true;
        } catch (SQLException exception) {
            return false;
        }
    }

    private byte[] zipWithLine(String line) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("data.tsv"));
            zip.write((line + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }
}
