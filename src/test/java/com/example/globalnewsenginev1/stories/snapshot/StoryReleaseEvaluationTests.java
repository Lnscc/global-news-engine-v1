package com.example.globalnewsenginev1.stories.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoryReleaseEvaluationTests {
    @Test
    void exclusionManifestCoversTheEntireCalibrationCorpus() throws Exception {
        var corpus = new ObjectMapper().readTree(Path.of("docs/analysis/ART-032-corpus.json").toFile());
        var expected = new HashSet<String>();
        corpus.get("articles").forEach(article -> {
            expected.add(article.get("urlHash").asText());
            expected.add(article.get("titleInputHash").asText());
        });
        assertThat(Files.readAllLines(Path.of("src/main/resources/stories/art032-excluded-hashes.txt")))
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void rejectsCalibrationLeaksDuplicatesAndMissingProvenanceAndFailsSmallSamples() throws Exception {
        var first = new ObjectMapper().readTree(Path.of("docs/analysis/ART-032-corpus.json").toFile()).get("articles").get(0);
        String ref = first.get("urlHash").asText();
        String second = "f".repeat(64);
        var pair = new StoryReleaseEvaluation.Pair(ref, second, true, "reason");
        var assignments = Map.of(ref, UUID.randomUUID(), second, UUID.randomUUID());
        assertThatThrownBy(() -> StoryReleaseEvaluation.evaluate(holdout(List.of(pair)), assignments,
                Map.of(ref, "new-title", second, "other-title"))).hasMessageContaining("ART-032");
        var fresh = new StoryReleaseEvaluation.Pair("a", "b", true, "reason");
        var freshAssignments = Map.of("a", UUID.randomUUID(), "b", UUID.randomUUID());
        assertThatThrownBy(() -> StoryReleaseEvaluation.evaluate(holdout(List.of(fresh)), freshAssignments,
                Map.of("a", first.get("titleInputHash").asText(), "b", "other-title"))).hasMessageContaining("ART-032");
        assertThatThrownBy(() -> StoryReleaseEvaluation.evaluate(holdout(List.of(fresh, fresh)), freshAssignments,
                Map.of("a", "new", "b", "other"))).hasMessageContaining("unique");
        assertThatThrownBy(() -> StoryReleaseEvaluation.evaluate(
                new StoryReleaseEvaluation.Holdout("new-v1", "", "reviewer", "independent", List.of(fresh)),
                freshAssignments, Map.of("a", "new", "b", "other"))).hasMessageContaining("provenance");
        assertThat(StoryReleaseEvaluation.evaluate(holdout(List.of(fresh)), freshAssignments,
                Map.of("a", "new", "b", "other")).passed()).isFalse();
    }

    private StoryReleaseEvaluation.Holdout holdout(List<StoryReleaseEvaluation.Pair> pairs) {
        return new StoryReleaseEvaluation.Holdout("new-v1", "synthetic-test", "reviewer", "independent", pairs);
    }
}
