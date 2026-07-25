package com.example.globalnewsenginev1.stories.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
class OpenAiEmbeddingClient implements EmbeddingClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final String apiKey;
    private final Duration timeout;

    @Autowired
    OpenAiEmbeddingClient(
            @Value("${stories.embeddings.base-url:https://api.openai.com/v1}") URI baseUrl,
            @Value("${stories.embeddings.api-key:}") String apiKey,
            @Value("${stories.embeddings.timeout:PT30S}") Duration timeout
    ) {
        this(HttpClient.newBuilder().connectTimeout(timeout).build(), new ObjectMapper(),
                baseUrl.resolve(baseUrl.toString().endsWith("/") ? "embeddings" : baseUrl + "/embeddings"),
                apiKey, timeout);
    }

    OpenAiEmbeddingClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            URI endpoint,
            String apiKey,
            Duration timeout
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.timeout = timeout;
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public List<EmbeddingResponse> embed(String modelId, int dimension, List<String> inputs) {
        if (!isAvailable()) {
            throw new IllegalStateException("Embedding API key is not configured");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", modelId);
            body.put("input", inputs);
            body.put("encoding_format", "float");
            body.put("dimensions", dimension);
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String requestId = response.headers().firstValue("x-request-id").orElse(null);
            if (response.statusCode() / 100 != 2) {
                throw providerError(response, requestId);
            }
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            List<EmbeddingResponse> result = new ArrayList<>();
            for (JsonNode item : data) {
                int index = item.path("index").asInt(-1);
                JsonNode values = item.path("embedding");
                float[] vector = new float[values.size()];
                for (int coordinate = 0; coordinate < values.size(); coordinate++) {
                    vector[coordinate] = values.get(coordinate).floatValue();
                }
                result.add(new EmbeddingResponse(index, vector, requestId));
            }
            result.sort(Comparator.comparingInt(EmbeddingResponse::index));
            if (result.size() != inputs.size()) {
                throw new EmbeddingClientException("INCOMPLETE_BATCH",
                        "Provider returned %d of %d embeddings".formatted(result.size(), inputs.size()),
                        true, null, requestId, null);
            }
            return result;
        } catch (HttpTimeoutException exception) {
            throw new EmbeddingClientException("TIMEOUT", "Embedding request timed out",
                    true, null, null, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new EmbeddingClientException("INTERRUPTED", "Embedding request was interrupted",
                    true, null, null, exception);
        } catch (IOException exception) {
            throw new EmbeddingClientException("IO_ERROR", "Embedding request failed",
                    true, null, null, exception);
        }
    }

    private EmbeddingClientException providerError(HttpResponse<String> response, String requestId) {
        int status = response.statusCode();
        boolean retryable = status == 408 || status == 409 || status == 429 || status >= 500;
        String code = "HTTP_" + status;
        String message = "Embedding provider returned HTTP " + status;
        try {
            JsonNode error = objectMapper.readTree(response.body()).path("error");
            code = error.path("code").asText(code);
            message = error.path("message").asText(message);
        } catch (IOException ignored) {
            // The status and request id remain sufficient and avoid logging arbitrary response bodies.
        }
        Duration retryAfter = status == 429
                ? response.headers().firstValue("retry-after").map(this::parseRetryAfter).orElse(null)
                : null;
        return new EmbeddingClientException(code, message, retryable, retryAfter, requestId, null);
    }

    private Duration parseRetryAfter(String value) {
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(value)));
        } catch (NumberFormatException ignored) {
            try {
                return Duration.between(ZonedDateTime.now(), ZonedDateTime.parse(
                        value, DateTimeFormatter.RFC_1123_DATE_TIME)).isNegative()
                        ? Duration.ZERO
                        : Duration.between(ZonedDateTime.now(), ZonedDateTime.parse(
                                value, DateTimeFormatter.RFC_1123_DATE_TIME));
            } catch (RuntimeException invalidDate) {
                return null;
            }
        }
    }
}
