package com.example.globalnewsenginev1.articles.enrichment;

final class ArticleCrawlException extends Exception {
    private final String code;
    private final boolean retryable;

    ArticleCrawlException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    String code() { return code; }
    boolean retryable() { return retryable; }
}
