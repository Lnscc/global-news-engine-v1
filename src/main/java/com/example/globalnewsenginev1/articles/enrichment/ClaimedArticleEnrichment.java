package com.example.globalnewsenginev1.articles.enrichment;

public record ClaimedArticleEnrichment(long articleId, String canonicalUrl, int attemptCount) {
}
