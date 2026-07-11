package com.example.globalnewsenginev1.articles.normalization;

public class ArticleUrlNormalizationException extends RuntimeException {

    private final String code;

    public ArticleUrlNormalizationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
