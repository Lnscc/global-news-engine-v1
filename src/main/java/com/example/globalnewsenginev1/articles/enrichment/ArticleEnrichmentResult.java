package com.example.globalnewsenginev1.articles.enrichment;

import java.time.Instant;

public record ArticleEnrichmentResult(
        String title,
        Instant publishedAt,
        String language,
        String mainImageUrl,
        String extractedText
) {
}
