package com.example.globalnewsenginev1.articles;

class ArticleUrlNormalizationException extends RuntimeException {

    private final String code;

    ArticleUrlNormalizationException(String code, String message) {
        super(message);
        this.code = code;
    }

    String code() {
        return code;
    }
}
