package com.example.globalnewsenginev1.stories.embedding;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Component
class StoryEmbeddingMetrics {

    private final Counter modelCalls;
    private final Counter attempts;
    private final Counter failures;
    private final Counter processed;
    private final Timer latency;

    StoryEmbeddingMetrics(MeterRegistry registry, StoryEmbeddingRepository repository) {
        modelCalls = registry.counter("stories.embedding.model.calls");
        attempts = registry.counter("stories.embedding.attempts");
        failures = registry.counter("stories.embedding.failures");
        processed = registry.counter("stories.embedding.inputs.processed");
        latency = registry.timer("stories.embedding.provider.latency");
        Gauge.builder("stories.embedding.artifacts", repository,
                        value -> value.healthSnapshot().pending())
                .tag("status", "PENDING").register(registry);
        Gauge.builder("stories.embedding.artifacts", repository,
                        value -> value.healthSnapshot().retryable())
                .tag("status", "RETRYABLE_FAILURE").register(registry);
        Gauge.builder("stories.embedding.artifacts", repository,
                        value -> value.healthSnapshot().exhausted())
                .tag("status", "RETRY_EXHAUSTED").register(registry);
        Gauge.builder("stories.embedding.artifacts", repository,
                        value -> value.healthSnapshot().terminal())
                .tag("status", "TERMINAL_FAILURE").register(registry);
        Gauge.builder("stories.embedding.backlog", repository, value -> {
            StoryEmbeddingHealth.Snapshot snapshot = value.healthSnapshot();
            return snapshot.pending() + snapshot.retryable() + snapshot.exhausted();
        }).register(registry);
    }

    void modelCall() {
        modelCalls.increment();
    }

    void attempt() {
        attempts.increment();
    }

    void failure() {
        failures.increment();
    }

    void processed() {
        processed.increment();
    }

    void recordLatency(long nanos) {
        latency.record(nanos, java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    @Configuration
    static class RegistryConfiguration {
        @Bean
        @ConditionalOnMissingBean(MeterRegistry.class)
        MeterRegistry storyMeterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
