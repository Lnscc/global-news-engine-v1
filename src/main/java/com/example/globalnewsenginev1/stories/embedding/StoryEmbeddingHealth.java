package com.example.globalnewsenginev1.stories.embedding;

import org.springframework.stereotype.Service;

@Service
public class StoryEmbeddingHealth {

    private final EmbeddingClient client;
    private final StoryEmbeddingRepository repository;

    StoryEmbeddingHealth(EmbeddingClient client, StoryEmbeddingRepository repository) {
        this.client = client;
        this.repository = repository;
    }

    public Report report() {
        Snapshot snapshot = repository.healthSnapshot();
        String status;
        if (!client.isAvailable()) {
            status = "MODEL_CALLS_DISABLED";
        } else if (snapshot.exhausted() > 0 || snapshot.terminal() > 0) {
            status = "DEGRADED";
        } else {
            status = "UP";
        }
        return new Report(status, client.isAvailable(), snapshot.pending(), snapshot.retryable(),
                snapshot.exhausted(), snapshot.terminal());
    }

    public record Report(
            String status,
            boolean modelCallsEnabled,
            int pending,
            int retryable,
            int exhausted,
            int terminal
    ) {
    }

    record Snapshot(int pending, int retryable, int exhausted, int terminal) {
    }
}
