package com.example.globalnewsenginev1.articles.query;

import java.time.Instant;

public record ArticleSummary(
        long id,
        String canonicalUrl,
        String domain,
        Instant firstSeenAt,
        String title,
        String titleSource,
        Instant publishedAt,
        String publishedAtSource
) {
}
