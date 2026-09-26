package com.example.globalnewsenginev1.stories.snapshot;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/** Run with -DargLine=-Xmx64m to catch retention of the 2.4 million pair scores. */
class StoryPartitionMemoryTests {
    @Test
    void partitionsLargeSparseSnapshotWithoutRetainingEveryPair() {
        int size = 2200;
        int dimension = 64;
        var inputs = new ArrayList<StorySnapshotRepository.SnapshotInput>();
        Random random = new Random(38);
        for (int i = 0; i < size; i++) {
            ByteBuffer buffer = ByteBuffer.allocate(dimension * Float.BYTES);
            for (int j = 0; j < dimension; j++) buffer.putFloat(random.nextBoolean() ? 1 : -1);
            byte[] bytes = buffer.array();
            String ref = "%064d".formatted(i);
            inputs.add(new StorySnapshotRepository.SnapshotInput(i, ref, ref, Instant.EPOCH,
                    "PUBLISHED_AT", ref, i, StorySnapshotCanonicalizer.sha256(bytes), bytes, dimension));
        }
        var rules = new StoryPartitionService.SnapshotRules(1, "snapshot", "hash", "version",
                dimension, 24, new BigDecimal("0.700000"), "exact-cosine-radius-v1",
                StoryPartitionService.COMPONENT_RULE);
        var result = StoryPartitionService.partition(rules, inputs, false);
        assertThat(result.components()).hasSize(size);
        assertThat(result.decisions()).isEmpty();
        assertThat(result.components()).allSatisfy(component -> assertThat(component.members()).hasSize(1));
        var version = new StorySnapshotRepository.ClusteringVersion(1, "version", dimension, 24,
                new BigDecimal("0.700000"), "exact-cosine-radius-v1", "pair-rule");
        var search = StorySnapshotService.search(version, inputs);
        assertThat(search.comparedPairs()).isEqualTo((long) size * (size - 1) / 2);
        assertThat(StoryPartitionService.partitionForPublication(rules, inputs, search.decisions()).components())
                .isEqualTo(result.components());
    }
}
