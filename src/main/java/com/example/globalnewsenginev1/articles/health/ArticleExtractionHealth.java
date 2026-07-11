package com.example.globalnewsenginev1.articles.health;

import java.util.List;

public record ArticleExtractionHealth(
        long articlesCreatedTotal,
        List<SignalTypeExtractionHealth> signalTypes
) {
}
