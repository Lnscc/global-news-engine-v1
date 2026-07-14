package com.example.globalnewsenginev1.articles.query;

import java.time.Instant;
import java.util.List;

public record ArticleDetail(
        long id,
        String canonicalUrl,
        String domain,
        Instant firstSeenAt,
        String title,
        String titleSource,
        Instant publishedAt,
        String publishedAtSource,
        String mainImageUrl,
        String mainImageSource,
        Instant createdAt,
        Instant updatedAt,
        List<ArticleSignal> signals
) {
    public ArticleDetail {
        signals = List.copyOf(signals);
    }
}
