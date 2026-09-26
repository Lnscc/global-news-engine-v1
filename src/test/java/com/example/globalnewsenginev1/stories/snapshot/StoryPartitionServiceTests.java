package com.example.globalnewsenginev1.stories.snapshot;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoryPartitionServiceTests {
    private static final Instant START = Instant.parse("2026-07-25T00:00:00Z");

    @Test
    void partitionsEmptyInputsAndIsolatedArticlesWithoutLosingMembers() {
        assertThat(partition(List.of()).components()).isEmpty();
        var result = partition(List.of(input("c", 49, 1, 0), input("b", 0, 0, 1),
                input("a", 0, 1, 0)));
        assertThat(groups(result)).containsExactly(List.of("a"), List.of("b"), List.of("c"));
        assertThat(result.decisions()).isEmpty();
        assertThat(result.components()).allSatisfy(component -> {
            assertThat(component.medoidArticleRef()).isEqualTo(component.members().getFirst().articleRef());
            assertThat(component.members().getFirst().similarityToMedoid()).isEqualByComparingTo("1");
        });
    }

    @Test
    void choosesHighestMeanMedoidAndUsesTimeThenReferenceForTies() {
        var result = partition(List.of(angle("a", 0, 0), angle("b", 0, 20), angle("c", 0, 40)));
        assertThat(groups(result)).containsExactly(List.of("a", "b", "c"));
        assertThat(result.components().getFirst().medoidArticleRef()).isEqualTo("b");
        var ties = partition(List.of(angle("a", 1, 0), angle("z", 0, 0), angle("b", 0, 0)));
        assertThat(ties.components().getFirst().medoidArticleRef()).isEqualTo("b");
        // Merge priority uses articleRef, independently of the medoid's effectiveAt tie-break.
        assertThat(ties.decisions().getFirst().leftArticleRefs()).containsExactly("a");
        assertThat(ties.decisions().getFirst().rightArticleRefs()).containsExactly("b");
    }

    @Test
    void appliesQuantizedSimilarityAndInclusiveVersionedTimeBoundaries() {
        for (int hours : List.of(24, 48, 72)) {
            var boundary = input("b", hours, 0.6999998f, (float) Math.sqrt(1 - 0.6999998 * 0.6999998));
            var inside = StoryPartitionService.partition(rules(hours), List.of(input("a", 0, 1, 0), boundary));
            assertThat(inside.components()).hasSize(1);
            assertThat(inside.decisions().getFirst().medoidSimilarity()).isEqualByComparingTo("0.700000");
            var late = new StorySnapshotRepository.SnapshotInput(boundary.articleInputId(), "b",
                    boundary.articleInputFingerprint(), boundary.effectiveAt().plusNanos(1000),
                    "PUBLISHED_AT", boundary.titleInputHash(), boundary.embeddingArtifactId(),
                    boundary.vectorHash(), boundary.vectorBytes(), 2);
            assertThat(StoryPartitionService.partition(rules(hours),
                    List.of(input("a", 0, 1, 0), late)).components()).hasSize(2);
        }
        assertThat(partition(List.of(input("a", 0, 1, 0),
                input("b", 0, 0.699999f, (float) Math.sqrt(1 - 0.699999 * 0.699999))))
                .components()).hasSize(2);
    }

    @Test
    void rejectsSingleLinkageChainAndReportsViolatingRadius() {
        var result = partition(List.of(angle("a", 0, 45), angle("b", 0, 175),
                angle("c", 0, 80), angle("d", 0, 105), angle("e", 0, 30), angle("f", 0, 10)));
        assertThat(groups(result)).containsExactly(List.of("a", "e", "f"), List.of("b"), List.of("c", "d"));
        var rejected = result.decisions().stream().filter(d -> !d.accepted()).toList();
        assertThat(rejected).hasSize(1);
        assertThat(rejected.getFirst().members()).anySatisfy(member ->
                assertThat(member.similarityToMedoid()).isLessThan(new BigDecimal("0.700000")));
        assertThat(result.components()).allSatisfy(component ->
                assertThat(component.members()).allSatisfy(member ->
                        assertThat(member.similarityToMedoid()).isGreaterThanOrEqualTo(new BigDecimal("0.7"))));
    }

    @Test
    void retriesRejectedUnionWhenAnotherMergeChangesItsMedoid() {
        var inputs = List.of(angle("a", 42, 100), angle("b", 48, 55), angle("c", 12, 15),
                angle("d", 6, 55), angle("e", 24, 75), angle("f", 30, 10));
        var result = partition(inputs);
        assertThat(groups(result)).containsExactly(List.of("d", "e", "a", "b"), List.of("c", "f"));
        var rejected = result.decisions().stream().filter(d -> !d.accepted()).findFirst().orElseThrow();
        assertThat(rejected.proposedMedoidArticleRef()).isEqualTo("d");
        assertThat(rejected.members()).anySatisfy(member ->
                assertThat(member.timeDistance()).isGreaterThan(Duration.ofHours(24)));
        var last = result.decisions().getLast();
        assertThat(last.accepted()).isTrue();
        assertThat(last.proposedMedoidArticleRef()).isEqualTo("e");
        assertThat(last.members()).extracting(StoryPartitionService.MemberEvidence::articleRef)
                .containsExactly("d", "e", "a", "b");
        for (int seed = 0; seed < 30; seed++) {
            var shuffled = new ArrayList<>(inputs);
            Collections.shuffle(shuffled, new Random(seed));
            assertThat(partition(shuffled)).isEqualTo(result);
        }
    }

    @Test
    void rejectsUnsupportedRulesDuplicateArticlesAndInvalidArtifacts() {
        var valid = input("a", 0, 1, 0);
        assertThatThrownBy(() -> partition(List.of(valid, valid)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> StoryPartitionService.partition(rules(25), List.of(valid)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsupported");
        var unsupported = new StoryPartitionService.SnapshotRules(1, "s", "h", "v", 2, 24,
                new BigDecimal("0.7"), "exact-cosine-radius-v1", "unknown-rule");
        assertThatThrownBy(() -> StoryPartitionService.partition(unsupported, List.of(valid)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsupported");
        assertThatThrownBy(() -> partition(List.of(input("a", 0, 0, 0))))
                .isInstanceOf(ExactCosine.InvalidVectorException.class);
        var wrongDimension = new StorySnapshotRepository.SnapshotInput(1, "a", "fp", START,
                "PUBLISHED_AT", "title", 1, valid.vectorHash(), valid.vectorBytes(), 3);
        assertThatThrownBy(() -> partition(List.of(wrongDimension)))
                .isInstanceOfSatisfying(ExactCosine.InvalidVectorException.class,
                        e -> assertThat(e.code()).isEqualTo("DIMENSION_MISMATCH"));
        valid.vectorBytes()[0] ^= 1;
        assertThatThrownBy(() -> partition(List.of(valid)))
                .isInstanceOfSatisfying(ExactCosine.InvalidVectorException.class,
                        e -> assertThat(e.code()).isEqualTo("VECTOR_HASH"));
    }

    private static StoryPartitionService.Partition partition(List<StorySnapshotRepository.SnapshotInput> inputs) {
        var diagnostic = StoryPartitionService.partition(rules(24), inputs);
        var publication = StoryPartitionService.partition(rules(24), inputs, false);
        assertThat(publication.components()).isEqualTo(diagnostic.components());
        assertThat(publication.decisions()).isEmpty();
        var sorted = inputs.stream().sorted(java.util.Comparator
                .comparing(StorySnapshotRepository.SnapshotInput::effectiveAt)
                .thenComparing(StorySnapshotRepository.SnapshotInput::articleRef)).toList();
        var version = new StorySnapshotRepository.ClusteringVersion(1, "version", 2, 24,
                new BigDecimal("0.700000"), "exact-cosine-radius-v1", "pair-rule");
        var pairs = StorySnapshotService.search(version, sorted);
        assertThat(StoryPartitionService.partitionForPublication(rules(24), sorted, pairs.decisions()).components())
                .isEqualTo(diagnostic.components());
        return diagnostic;
    }

    private static StoryPartitionService.SnapshotRules rules(int window) {
        return new StoryPartitionService.SnapshotRules(1, "snapshot", "input-hash", "version", 2,
                window, new BigDecimal("0.700000"), "exact-cosine-radius-v1", StoryPartitionService.COMPONENT_RULE);
    }

    private static List<List<String>> groups(StoryPartitionService.Partition result) {
        return result.components().stream().map(component -> component.members().stream()
                .map(StoryPartitionService.MemberEvidence::articleRef).toList()).toList();
    }

    private static StorySnapshotRepository.SnapshotInput angle(String ref, long hours, double degrees) {
        return input(ref, hours, (float) Math.cos(Math.toRadians(degrees)),
                (float) Math.sin(Math.toRadians(degrees)));
    }

    private static StorySnapshotRepository.SnapshotInput input(String ref, long hours, float x, float y) {
        byte[] bytes = ByteBuffer.allocate(8).putFloat(x).putFloat(y).array();
        return new StorySnapshotRepository.SnapshotInput(ref.charAt(0), ref, "fingerprint-" + ref,
                START.plus(Duration.ofHours(hours)), "PUBLISHED_AT", "title-" + ref, ref.charAt(0),
                StorySnapshotCanonicalizer.sha256(bytes), bytes, 2);
    }
}
