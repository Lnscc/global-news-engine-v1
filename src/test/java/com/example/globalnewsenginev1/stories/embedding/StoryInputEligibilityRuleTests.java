package com.example.globalnewsenginev1.stories.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoryInputEligibilityRuleTests {

    @Test
    void matchesTheArt045InputQualityCases() throws Exception {
        JsonNode corpus = new ObjectMapper().readTree(Path.of(
                "docs/analysis/ART-045-regression-cases-v1.json").toFile());
        Map<String, JsonNode> articles = new HashMap<>();
        corpus.path("articles").forEach(article ->
                articles.put(article.path("ref").asText(), article));

        for (JsonNode testCase : corpus.path("inputQualityCases")) {
            JsonNode article = articles.get(testCase.path("articleRef").asText());
            var decision = StoryInputEligibilityRule.evaluate(
                    StoryInputEligibilityRule.NAVIGATION_SERVICE_VERSION,
                    article.path("canonicalUrl").asText(), article.path("title").asText());

            assertThat(decision.disposition())
                    .as(testCase.path("id").asText())
                    .isEqualTo(testCase.path("expectedDisposition").asText());
            if (decision.excluded()) {
                assertThat(decision.reason())
                        .isEqualTo(StoryInputEligibilityRule.NAVIGATION_SERVICE_REASON);
            }
        }
    }

    @Test
    void doesNotMatchNavigationWordsInsideArticlePathsOrTitlesAlone() {
        assertThat(StoryInputEligibilityRule.evaluate(
                StoryInputEligibilityRule.NAVIGATION_SERVICE_VERSION,
                "https://example.org/news/privacy-policy-changes", "Privacy Policy Changes Today")
                .excluded()).isFalse();
        assertThat(StoryInputEligibilityRule.evaluate(
                StoryInputEligibilityRule.NAVIGATION_SERVICE_VERSION,
                "https://example.org/privacy", "Government Announces Tax Changes")
                .excluded()).isFalse();
    }

    @Test
    void rejectsUnknownRuleVersionsInsteadOfChangingBehaviorSilently() {
        assertThatThrownBy(() -> StoryInputEligibilityRule.evaluate(
                "unknown", "https://example.org/about", "About Example"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }
}
