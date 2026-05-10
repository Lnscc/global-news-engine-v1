package com.example.globalnewsenginev1.articles;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

final class ArticleUrlNormalizer {

    private ArticleUrlNormalizer() {
    }

    static Optional<String> normalize(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return Optional.empty();
        }

        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || uri.getPath() == null) {
                return Optional.empty();
            }
            if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
                return Optional.empty();
            }

            URI normalized = new URI(
                    scheme.toLowerCase(Locale.ROOT),
                    uri.getUserInfo(),
                    host.toLowerCase(Locale.ROOT),
                    uri.getPort(),
                    normalizePath(uri.getPath()),
                    uri.getQuery(),
                    null
            );
            return Optional.of(normalized.toString());
        } catch (RuntimeException ex) {
            return Optional.empty();
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    static Optional<String> domainOf(String normalizedUrl) {
        try {
            return Optional.ofNullable(URI.create(normalizedUrl).getHost());
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }
}
