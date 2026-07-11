package com.example.globalnewsenginev1.articles.enrichment;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArticleCrawlerTests {
    private HttpServer server; private URI baseUri; private ArticleHttpClient client;

    @BeforeEach void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        client = new ArticleHttpClient(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                Duration.ofMillis(300), 1024, 2, ignored -> { });
    }
    @AfterEach void tearDown() { server.stop(0); }

    @Test void extractsPrioritizedMetadataAndResolvesImage() throws Exception {
        endpoint("/article", 200, "text/html; charset=UTF-8", """
            <html lang="de-DE"><head><title>Fallback</title><meta name="twitter:title" content="Twitter">
            <meta property="og:title" content="OG title"><meta property="article:published_time" content="2026-07-11T08:15:00Z">
            <meta property="og:image" content="/images/main.jpg"></head><body><nav>Menu</nav>
            <article><h1>Heading</h1><p>Useful body.</p></article><script>noise()</script></body></html>""");
        ArticleEnrichmentResult result = new ArticleHtmlParser().parse(client.fetch(baseUri + "/article"));
        assertThat(result.title()).isEqualTo("OG title");
        assertThat(result.publishedAt()).isEqualTo(Instant.parse("2026-07-11T08:15:00Z"));
        assertThat(result.language()).isEqualTo("de-DE");
        assertThat(result.mainImageUrl()).isEqualTo(baseUri + "/images/main.jpg");
        assertThat(result.extractedText()).isEqualTo("Heading Useful body.");
    }

    @Test void supportsPartialMetadataAndFallbacks() throws Exception {
        endpoint("/partial", 200, "text/html", "<html><head><title> Only title </title></head><body></body></html>");
        ArticleEnrichmentResult result = new ArticleHtmlParser().parse(client.fetch(baseUri + "/partial"));
        assertThat(result.title()).isEqualTo("Only title"); assertThat(result.publishedAt()).isNull();
    }

    @Test void followsBoundedRedirects() throws Exception {
        redirect("/one", "/two"); redirect("/two", "/final"); endpoint("/final", 200, "text/html", "<title>Final</title>");
        assertThat(client.fetch(baseUri + "/one").finalUri()).isEqualTo(baseUri.resolve("/final"));
        redirect("/loop", "/loop"); assertFailure(client, baseUri + "/loop", "TOO_MANY_REDIRECTS", false);
    }

    @Test void classifiesHttpSizeTimeoutAndContentFailures() {
        endpoint("/large", 200, "text/html", "x".repeat(1100)); endpoint("/missing", 404, "text/html", "missing");
        endpoint("/busy", 503, "text/html", "busy"); endpoint("/json", 200, "application/json", "{}");
        server.createContext("/slow", exchange -> { try { Thread.sleep(600); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            byte[] body = "<title>late</title>".getBytes(StandardCharsets.UTF_8);
            try { exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); } catch (java.io.IOException ignored) { } finally { exchange.close(); }});
        assertFailure(client, baseUri + "/large", "RESPONSE_TOO_LARGE", false);
        assertFailure(client, baseUri + "/missing", "HTTP_404", false); assertFailure(client, baseUri + "/busy", "HTTP_503", true);
        assertFailure(client, baseUri + "/json", "UNSUPPORTED_CONTENT_TYPE", false); assertFailure(client, baseUri + "/slow", "TIMEOUT", true);
    }

    @Test void rejectsForbiddenTargets() {
        ArticleHttpClient protectedClient = new ArticleHttpClient(HttpClient.newHttpClient(), Duration.ofSeconds(1), 1024, 1,
                ArticleHttpClient::validatePublicAddress);
        assertFailure(protectedClient, baseUri.toString(), "FORBIDDEN_ADDRESS", false);
    }

    private void endpoint(String path, int status, String type, String value) { server.createContext(path, exchange -> {
        byte[] body = value.getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().add("Content-Type", type);
        exchange.sendResponseHeaders(status, body.length); exchange.getResponseBody().write(body); exchange.close(); }); }
    private void redirect(String path, String location) { server.createContext(path, exchange -> {
        exchange.getResponseHeaders().add("Location", location); exchange.sendResponseHeaders(302, -1); exchange.close(); }); }
    private void assertFailure(ArticleHttpClient subject, String url, String code, boolean retryable) {
        assertThatThrownBy(() -> subject.fetch(url)).isInstanceOfSatisfying(ArticleCrawlException.class, ex -> {
            assertThat(ex.code()).isEqualTo(code); assertThat(ex.retryable()).isEqualTo(retryable); }); }
}
