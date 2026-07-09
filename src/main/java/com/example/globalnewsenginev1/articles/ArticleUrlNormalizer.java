package com.example.globalnewsenginev1.articles;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Component
class ArticleUrlNormalizer {

    NormalizedArticleUrl normalize(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            throw new ArticleUrlNormalizationException("EMPTY_URL", "URL is empty");
        }

        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException exception) {
            throw new ArticleUrlNormalizationException("INVALID_URL", exception.getMessage());
        }

        String scheme = lower(uri.getScheme());
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new ArticleUrlNormalizationException("UNSUPPORTED_SCHEME", "Only http and https URLs are supported");
        }

        String host = lower(uri.getHost());
        if (host == null || host.isBlank()) {
            throw new ArticleUrlNormalizationException("MISSING_HOST", "URL host is missing");
        }

        int port = normalizedPort(scheme, uri.getPort());
        String path = normalizedPath(uri.getRawPath());
        String query = normalizedQuery(uri.getRawQuery());

        String canonicalUrl;
        try {
            canonicalUrl = new URI(buildCanonicalUrl(scheme, host, port, path, query)).toASCIIString();
        } catch (URISyntaxException exception) {
            throw new ArticleUrlNormalizationException("INVALID_URL", exception.getMessage());
        }

        return new NormalizedArticleUrl(canonicalUrl, sha256(canonicalUrl), host);
    }

    private String buildCanonicalUrl(String scheme, String host, int port, String path, String query) {
        StringBuilder canonicalUrl = new StringBuilder();
        canonicalUrl.append(scheme).append("://").append(host);
        if (port >= 0) {
            canonicalUrl.append(':').append(port);
        }
        canonicalUrl.append(path);
        if (query != null) {
            canonicalUrl.append('?').append(query);
        }
        return canonicalUrl.toString();
    }

    private String normalizedPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return "";
        }
        String path = rawPath;
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private String normalizedQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }

        List<String> parameters = new ArrayList<>();
        for (String parameter : rawQuery.split("&")) {
            if (parameter.isBlank()) {
                continue;
            }
            String name = parameterName(parameter).toLowerCase(Locale.ROOT);
            if (name.startsWith("utm_") || "fbclid".equals(name) || "gclid".equals(name)) {
                continue;
            }
            parameters.add(parameter);
        }

        if (parameters.isEmpty()) {
            return null;
        }
        parameters.sort(Comparator.naturalOrder());
        return String.join("&", parameters);
    }

    private String parameterName(String parameter) {
        int separator = parameter.indexOf('=');
        return separator < 0 ? parameter : parameter.substring(0, separator);
    }

    private int normalizedPort(String scheme, int port) {
        if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
            return -1;
        }
        return port;
    }

    private String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
