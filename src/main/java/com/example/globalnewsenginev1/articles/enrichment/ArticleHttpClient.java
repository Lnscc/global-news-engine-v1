package com.example.globalnewsenginev1.articles.enrichment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

@Component
class ArticleHttpClient {
    private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);
    private final HttpClient client;
    private final Duration readTimeout;
    private final int maxResponseBytes;
    private final int maxRedirects;
    private final AddressValidator addressValidator;

    @Autowired
    ArticleHttpClient(
            @Value("${articles.enrichment.connect-timeout:PT5S}") Duration connectTimeout,
            @Value("${articles.enrichment.read-timeout:PT10S}") Duration readTimeout,
            @Value("${articles.enrichment.max-response-bytes:2097152}") int maxResponseBytes,
            @Value("${articles.enrichment.max-redirects:3}") int maxRedirects) {
        this(HttpClient.newBuilder().connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER).build(), readTimeout,
                maxResponseBytes, maxRedirects, ArticleHttpClient::validatePublicAddress);
    }

    ArticleHttpClient(HttpClient client, Duration readTimeout, int maxResponseBytes,
                      int maxRedirects, AddressValidator addressValidator) {
        if (maxResponseBytes <= 0 || maxRedirects < 0) throw new IllegalArgumentException("invalid crawl limits");
        this.client = client;
        this.readTimeout = readTimeout;
        this.maxResponseBytes = maxResponseBytes;
        this.maxRedirects = maxRedirects;
        this.addressValidator = addressValidator;
    }

    CrawledArticlePage fetch(String url) throws ArticleCrawlException {
        URI current;
        try { current = URI.create(url); }
        catch (IllegalArgumentException ex) { throw permanent("INVALID_URL", "Invalid target URL"); }

        for (int redirects = 0; ; redirects++) {
            addressValidator.validate(current);
            HttpResponse<InputStream> response;
            try {
                response = client.send(HttpRequest.newBuilder(current).timeout(readTimeout)
                                .header("Accept", "text/html,application/xhtml+xml")
                                .header("User-Agent", "GlobalNewsEngine/1.0").GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
            } catch (java.net.http.HttpTimeoutException ex) {
                throw temporary("TIMEOUT", "Target request timed out");
            } catch (IOException ex) {
                throw temporary("CONNECTION_ERROR", "Target request failed: " + safe(ex.getMessage()));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw temporary("INTERRUPTED", "Target request interrupted");
            }

            int status = response.statusCode();
            if (REDIRECTS.contains(status)) {
                close(response.body());
                if (redirects >= maxRedirects) throw permanent("TOO_MANY_REDIRECTS", "Redirect limit exceeded");
                String location = response.headers().firstValue("location")
                        .orElseThrow(() -> permanent("INVALID_REDIRECT", "Redirect has no Location header"));
                try { current = current.resolve(location); }
                catch (IllegalArgumentException ex) { throw permanent("INVALID_REDIRECT", "Invalid redirect target"); }
                continue;
            }
            if (status == 429 || status >= 500) {
                close(response.body());
                throw temporary("HTTP_" + status, "Target returned HTTP " + status);
            }
            if (status < 200 || status >= 300) {
                close(response.body());
                throw permanent("HTTP_" + status, "Target returned HTTP " + status);
            }
            String contentType = response.headers().firstValue("content-type").orElse("")
                    .toLowerCase(Locale.ROOT);
            if (!(contentType.startsWith("text/html") || contentType.startsWith("application/xhtml+xml"))) {
                close(response.body());
                throw permanent("UNSUPPORTED_CONTENT_TYPE", "Unsupported Content-Type: " + safe(contentType));
            }
            return new CrawledArticlePage(current, readLimited(response.body()));
        }
    }

    private String readLimited(InputStream input) throws ArticleCrawlException {
        try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            for (int read; (read = input.read(buffer)) >= 0; ) {
                total += read;
                if (total > maxResponseBytes) throw permanent("RESPONSE_TOO_LARGE", "Response size limit exceeded");
                output.write(buffer, 0, read);
            }
            return output.toString(java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw temporary("READ_ERROR", "Could not read target response: " + safe(ex.getMessage()));
        }
    }

    static void validatePublicAddress(URI uri) throws ArticleCrawlException {
        if (uri == null || uri.getHost() == null || !("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme())) || uri.getUserInfo() != null) {
            throw permanent("FORBIDDEN_ADDRESS", "Only public HTTP(S) target URLs are allowed");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw permanent("FORBIDDEN_ADDRESS", "Target resolves to a non-public address");
                }
            }
        } catch (java.net.UnknownHostException ex) {
            throw temporary("DNS_ERROR", "Target host could not be resolved");
        }
    }

    private static ArticleCrawlException permanent(String code, String message) { return new ArticleCrawlException(code, message, false); }
    private static ArticleCrawlException temporary(String code, String message) { return new ArticleCrawlException(code, message, true); }
    private static String safe(String value) { return value == null ? "unknown" : value.replaceAll("[\\r\\n\\t]+", " "); }
    private static void close(InputStream input) { try { input.close(); } catch (IOException ignored) { } }

    @FunctionalInterface
    interface AddressValidator { void validate(URI uri) throws ArticleCrawlException; }
}
