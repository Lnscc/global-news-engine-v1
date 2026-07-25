package com.example.globalnewsenginev1.stories.embedding;

import java.time.Duration;

public class EmbeddingClientException extends RuntimeException {

    private final String errorCode;
    private final boolean retryable;
    private final Duration retryAfter;
    private final String providerRequestId;

    public EmbeddingClientException(
            String errorCode,
            String message,
            boolean retryable,
            Duration retryAfter,
            String providerRequestId,
            Throwable cause
    ) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.retryAfter = retryAfter;
        this.providerRequestId = providerRequestId;
    }

    public String errorCode() {
        return errorCode;
    }

    public boolean retryable() {
        return retryable;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    public String providerRequestId() {
        return providerRequestId;
    }
}
