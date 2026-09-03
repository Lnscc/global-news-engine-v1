package com.example.globalnewsenginev1.stories.snapshot;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StoryPairSearchTests {

    @Test
    void stopsEachOrderedScanAtTheFirstInputOutsideTheTimeWindow() {
        byte[] vector = ByteBuffer.allocate(2 * Float.BYTES)
                .putFloat(1)
                .putFloat(0)
                .array();
        String vectorHash = StorySnapshotCanonicalizer.sha256(vector);
        Instant start = Instant.parse("2026-07-25T00:00:00Z");
        StorySnapshotRepository.ClusteringVersion version =
                new StorySnapshotRepository.ClusteringVersion(
                        1, "version", 2, 24, new BigDecimal("0.700000"),
                        "exact-cosine-radius-v1", "cosine-070-time24-v1");
        List<StorySnapshotRepository.SnapshotInput> inputs = List.of(
                input(1, "a", start, vector, vectorHash),
                input(2, "b", start.plusSeconds(3600), vector, vectorHash),
                input(3, "c", start.plusSeconds(26 * 3600L), vector, vectorHash),
                input(4, "d", start.plusSeconds(52 * 3600L), vector, vectorHash));

        StorySnapshotService.PairSearchResult result =
                StorySnapshotService.search(version, inputs);

        assertThat(result.comparedPairs()).isOne();
        assertThat(result.examinedPairs()).isEqualTo(4);
        assertThat(result.decisions()).hasSize(1);
        assertThat(result.decisions().getFirst().result()).isEqualTo("SAME_STORY");
    }

    private StorySnapshotRepository.SnapshotInput input(
            long id,
            String articleRef,
            Instant effectiveAt,
            byte[] vector,
            String vectorHash
    ) {
        return new StorySnapshotRepository.SnapshotInput(
                id,
                articleRef.repeat(64),
                Long.toString(id).repeat(64),
                effectiveAt,
                "PUBLISHED_AT",
                "e".repeat(64),
                id,
                vectorHash,
                vector,
                2);
    }
}
