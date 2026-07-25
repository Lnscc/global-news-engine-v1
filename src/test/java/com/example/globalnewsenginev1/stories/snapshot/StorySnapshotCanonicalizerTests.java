package com.example.globalnewsenginev1.stories.snapshot;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StorySnapshotCanonicalizerTests {

    @Test
    void snapshotWatermarkKeyAndInputHashAreStableAndSensitiveToInputs() {
        Instant nanos = Instant.parse("2026-07-25T10:15:30.123456789Z");
        Instant watermark = Instant.parse("2026-07-25T10:15:30.123456Z");
        StorySnapshotRepository.SnapshotInput first = input("a", 1);
        StorySnapshotRepository.SnapshotInput second = input("b", 2);

        String firstHash = StorySnapshotCanonicalizer.snapshotInputHash(
                "version-v1", nanos, List.of(first, second));
        String repeatedHash = StorySnapshotCanonicalizer.snapshotInputHash(
                "version-v1", watermark, List.of(first, second));
        String changedHash = StorySnapshotCanonicalizer.snapshotInputHash(
                "version-v1", watermark, List.of(first));
        String key = StorySnapshotCanonicalizer.snapshotKey(
                "version-v1", nanos, firstHash);

        assertThat(StorySnapshotCanonicalizer.normalizeWatermark(nanos))
                .isEqualTo(watermark);
        assertThat(firstHash).isEqualTo(repeatedHash)
                .matches("[0-9a-f]{64}")
                .isNotEqualTo(changedHash);
        assertThat(key).matches("[0-9a-f]{64}")
                .isEqualTo(StorySnapshotCanonicalizer.snapshotKey(
                        "version-v1", watermark, repeatedHash));
        assertThat(StorySnapshotCanonicalizer.snapshotKey(
                "version-v2", watermark, repeatedHash)).isNotEqualTo(key);
    }

    private StorySnapshotRepository.SnapshotInput input(String ref, long id) {
        return new StorySnapshotRepository.SnapshotInput(
                id,
                ref.repeat(64),
                Integer.toString((int) id).repeat(64),
                Instant.parse("2026-07-25T09:00:00Z").plusSeconds(id),
                "PUBLISHED_AT",
                "c".repeat(64),
                id,
                "d".repeat(64),
                new byte[8],
                2);
    }
}
