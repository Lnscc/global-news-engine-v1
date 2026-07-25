package com.example.globalnewsenginev1.stories.embedding;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoryTitleNormalizerTests {

    private final StoryTitleNormalizer normalizer = new StoryTitleNormalizer();

    @Test
    void normalizesHtmlUnicodeWhitespaceAndHashesUtf8Deterministically() {
        TitleInput input = normalizer.normalize("  Ｎｅｗｓ\u00a0&amp;\tAnalysis!  ");

        assertThat(input.normalizedTitle()).isEqualTo("News & Analysis!");
        assertThat(input.titleInputHash())
                .isEqualTo("a42c141a5b5894c913a806a517b6fbee60c819e789e820693aed112f55652a13");
        assertThat(input.usability()).isEqualTo(TitleInput.TitleUsability.USABLE);
    }

    @Test
    void preservesCaseAndPunctuationAndRecognizesAllVersionedGenericTitles() {
        assertThat(normalizer.normalize("Breaking: NEWS?!").normalizedTitle())
                .isEqualTo("Breaking: NEWS?!");
        for (String generic : List.of(
                "Deadline", "HEALTH", "ckia", "Npr News", "TARGETED NEWS SERVICE")) {
            assertThat(normalizer.normalize(generic).usability())
                    .isEqualTo(TitleInput.TitleUsability.TITLE_GENERIC);
            assertThat(normalizer.normalize(generic).titleInputHash()).isNull();
        }
    }

    @Test
    void marksNullAndWhitespaceOnlyTitlesMissing() {
        assertThat(normalizer.normalize(null).usability())
                .isEqualTo(TitleInput.TitleUsability.TITLE_MISSING);
        assertThat(normalizer.normalize("\t\u2003 ").usability())
                .isEqualTo(TitleInput.TitleUsability.TITLE_MISSING);
    }

    @Test
    void validatesAndCanonicallyNormalizesFloat32Vectors() {
        EmbeddingVectorCodec.EncodedVector vector =
                EmbeddingVectorCodec.encode(new float[]{3, 4}, 2);
        ByteBuffer bytes = ByteBuffer.wrap(vector.bytes());

        assertThat(bytes.getFloat()).isEqualTo(0.6f);
        assertThat(bytes.getFloat()).isEqualTo(0.8f);
        assertThat(vector.originalNorm()).isEqualTo(5.0);
        assertThat(vector.hash()).hasSize(64);
        assertThatThrownBy(() -> EmbeddingVectorCodec.encode(new float[]{1}, 2))
                .isInstanceOf(EmbeddingVectorCodec.InvalidVectorException.class)
                .hasMessageContaining("Expected 2");
        assertThatThrownBy(() -> EmbeddingVectorCodec.encode(new float[]{Float.NaN, 1}, 2))
                .isInstanceOf(EmbeddingVectorCodec.InvalidVectorException.class);
        assertThatThrownBy(() -> EmbeddingVectorCodec.encode(
                new float[]{Float.POSITIVE_INFINITY, 1}, 2))
                .isInstanceOf(EmbeddingVectorCodec.InvalidVectorException.class);
        assertThatThrownBy(() -> EmbeddingVectorCodec.encode(new float[]{0, 0}, 2))
                .isInstanceOf(EmbeddingVectorCodec.InvalidVectorException.class);
    }

    @Test
    void appliesTheVersionedBackoffAndStopsAutomaticRetriesAfterFiveFailures() {
        Instant completed = Instant.parse("2026-07-25T10:00:00Z");

        assertThat(StoryEmbeddingService.nextRetryAt(1, completed, null))
                .isEqualTo(completed.plus(Duration.ofMinutes(1)));
        assertThat(StoryEmbeddingService.nextRetryAt(2, completed, null))
                .isEqualTo(completed.plus(Duration.ofMinutes(5)));
        assertThat(StoryEmbeddingService.nextRetryAt(3, completed, null))
                .isEqualTo(completed.plus(Duration.ofMinutes(15)));
        assertThat(StoryEmbeddingService.nextRetryAt(4, completed, null))
                .isEqualTo(completed.plus(Duration.ofHours(1)));
        assertThat(StoryEmbeddingService.nextRetryAt(4, completed, Duration.ofHours(2)))
                .isEqualTo(completed.plus(Duration.ofHours(2)));
        assertThat(StoryEmbeddingService.nextRetryAt(5, completed, null)).isNull();
    }
}
