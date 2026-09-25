package com.example.globalnewsenginev1.stories.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StoryPartitionCorpusTests {

    @Test
    @EnabledIfSystemProperty(named = "art037.inputs", matches = ".+")
    void evaluatesActualPartitionSeparatelyForEachFrozenCorpusSplit() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        byte[] corpusBytes = Files.readAllBytes(Path.of("docs/analysis/ART-032-corpus.json"));
        byte[] fixtureBytes = Files.readAllBytes(Path.of(System.getProperty("art037.inputs")));
        JsonNode corpus = mapper.readTree(corpusBytes);
        JsonNode fixture = mapper.readTree(fixtureBytes);
        assertThat(fixture.path("corpusFileHash").asText())
                .isEqualTo(StorySnapshotCanonicalizer.sha256(corpusBytes));
        Map<String, String> splitByRef = new HashMap<>();
        for (String split : List.of("calibration", "evaluation")) {
            List<StorySnapshotRepository.SnapshotInput> inputs = new ArrayList<>();
            Map<String, String> urlHashByRef = new HashMap<>();
            for (JsonNode node : fixture.path("splits").path(split)) {
                String ref = node.path("articleRef").asText();
                assertThat(splitByRef.put(ref, split)).isNull();
                urlHashByRef.put(node.path("corpusRef").asText(), ref);
                inputs.add(new StorySnapshotRepository.SnapshotInput(node.path("id").asLong(), ref,
                        node.path("fingerprint").asText(), Instant.parse(node.path("effectiveAt").asText()),
                        node.path("timeSource").asText(), node.path("titleInputHash").asText(),
                        node.path("id").asLong(), node.path("vectorHash").asText(),
                        Base64.getDecoder().decode(node.path("vectorBytes").asText()), 1536));
            }
            var rules = new StoryPartitionService.SnapshotRules(0, "art032-" + split,
                    StorySnapshotCanonicalizer.sha256(fixtureBytes), "art037-corpus-24h", 1536, 24,
                    new BigDecimal("0.700000"), "exact-cosine-radius-v1", StoryPartitionService.COMPONENT_RULE);
            var partition = StoryPartitionService.partition(rules, inputs);
            Collections.reverse(inputs);
            assertThat(StoryPartitionService.partition(rules, inputs)).isEqualTo(partition);
            Map<String, String> componentByRef = new HashMap<>();
            for (var component : partition.components()) {
                for (var member : component.members()) {
                    assertThat(componentByRef.put(member.articleRef(), component.medoidArticleRef())).isNull();
                    assertThat(member.similarityToMedoid()).isGreaterThanOrEqualTo(rules.threshold());
                    assertThat(member.timeDistance()).isLessThanOrEqualTo(java.time.Duration.ofHours(24));
                }
            }
            assertThat(componentByRef).hasSize(inputs.size());
            List<Map<String, Object>> predictions = new ArrayList<>();
            var expectedRefs = new HashSet<String>();
            for (JsonNode pair : corpus.path("pairs")) {
                if (!split.equals(pair.path("split").asText())) {
                    continue;
                }
                String left = urlHashByRef.get(pair.path("left").asText());
                String right = urlHashByRef.get(pair.path("right").asText());
                assertThat(left).isNotNull();
                assertThat(right).isNotNull();
                expectedRefs.add(left);
                expectedRefs.add(right);
                predictions.add(Map.of("pairId", pair.path("id").asText(),
                        "sameStory", componentByRef.get(left).equals(componentByRef.get(right))));
            }
            for (JsonNode story : corpus.path("referenceStories")) {
                if (split.equals(story.path("split").asText())) {
                    for (JsonNode ref : story.path("articleRefs")) {
                        assertThat(urlHashByRef).containsKey(ref.asText());
                        expectedRefs.add(urlHashByRef.get(ref.asText()));
                    }
                }
            }
            assertThat(componentByRef.keySet()).containsExactlyInAnyOrderElementsOf(expectedRefs);
            Path output = Path.of("target/art037-" + split + "-predictions.json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), Map.of(
                    "corpusVersion", fixture.path("corpusVersion").asText(),
                    "cacheFileHash", fixture.path("cacheFileHash").asText(),
                    "split", split, "pairs", predictions,
                    "components", partition.components().stream().map(component -> Map.of(
                            "medoid", component.medoidArticleRef(), "members", component.members().stream()
                                    .map(StoryPartitionService.MemberEvidence::articleRef).toList())).toList()));
        }
    }
}
