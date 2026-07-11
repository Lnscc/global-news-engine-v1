package com.example.globalnewsenginev1.articles.extraction;

public record ArticleExtractionResult(
        long articlesCreated,
        long signalsCreated,
        long errorsCreated
) {
    public ArticleExtractionResult plus(ArticleExtractionResult other) {
        return new ArticleExtractionResult(
                articlesCreated + other.articlesCreated,
                signalsCreated + other.signalsCreated,
                errorsCreated + other.errorsCreated);
    }

    public long totalProcessed() {
        return articlesCreated + signalsCreated + errorsCreated;
    }
}
