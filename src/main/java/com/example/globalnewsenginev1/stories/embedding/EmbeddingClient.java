package com.example.globalnewsenginev1.stories.embedding;

import java.util.List;

public interface EmbeddingClient {

    boolean isAvailable();

    List<EmbeddingResponse> embed(String modelId, int dimension, List<String> inputs);

    record EmbeddingResponse(int index, float[] vector, String providerRequestId) {
    }
}
