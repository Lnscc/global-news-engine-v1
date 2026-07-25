package com.example.globalnewsenginev1.stories.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiEmbeddingClientTests {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void mapsBatchEntriesByProviderIndexAndRequestId() throws Exception {
        start(exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer secret");
            assertThat(request).contains("\"model\":\"model\"", "\"dimensions\":2",
                    "\"input\":[\"first\",\"second\"]");
            byte[] response = """
                    {"data":[
                      {"index":1,"embedding":[0.0,1.0]},
                      {"index":0,"embedding":[1.0,0.0]}
                    ]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("x-request-id", "req-123");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        OpenAiEmbeddingClient client = client();

        List<EmbeddingClient.EmbeddingResponse> result =
                client.embed("model", 2, List.of("first", "second"));

        assertThat(result).extracting(EmbeddingClient.EmbeddingResponse::index)
                .containsExactly(0, 1);
        assertThat(result.getFirst().vector()).containsExactly(1.0f, 0.0f);
        assertThat(result.getFirst().providerRequestId()).isEqualTo("req-123");
    }

    @Test
    void exposesRateLimitRetryAfterWithoutLeakingTheApiKey() throws Exception {
        start(exchange -> {
            byte[] response = """
                    {"error":{"code":"rate_limit_exceeded","message":"slow down"}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Retry-After", "120");
            exchange.getResponseHeaders().add("x-request-id", "req-rate");
            exchange.sendResponseHeaders(429, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        assertThatThrownBy(() -> client().embed("model", 2, List.of("title")))
                .isInstanceOfSatisfying(EmbeddingClientException.class, exception -> {
                    assertThat(exception.retryable()).isTrue();
                    assertThat(exception.retryAfter()).isEqualTo(Duration.ofMinutes(2));
                    assertThat(exception.errorCode()).isEqualTo("rate_limit_exceeded");
                    assertThat(exception.providerRequestId()).isEqualTo("req-rate");
                    assertThat(exception.getMessage()).doesNotContain("secret");
                });
    }

    private OpenAiEmbeddingClient client() {
        return new OpenAiEmbeddingClient(HttpClient.newHttpClient(), new ObjectMapper(),
                URI.create("http://localhost:" + server.getAddress().getPort() + "/embeddings"),
                "secret", Duration.ofSeconds(2));
    }

    private void start(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/embeddings", handler);
        server.start();
    }
}
