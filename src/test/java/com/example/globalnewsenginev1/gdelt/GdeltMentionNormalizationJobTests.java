package com.example.globalnewsenginev1.gdelt;

import com.example.globalnewsenginev1.ingestion.RawSourceFile;
import com.example.globalnewsenginev1.ingestion.SourceBatch;
import com.example.globalnewsenginev1.ingestion.StagingRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GdeltMentionNormalizationJobTests {

    @Test
    void normalizesUnprocessedMentionRows() {
        GdeltMentionRepository mentionRepository = mock(GdeltMentionRepository.class);
        StagingRow row = stagingRow(GdeltMentionParserTests.mentionLine());

        when(mentionRepository.findUnnormalizedRows("MENTIONS")).thenReturn(List.of(row));
        when(mentionRepository.save(any(GdeltMention.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GdeltMentionNormalizationJob job = new GdeltMentionNormalizationJob(
                mentionRepository,
                new GdeltMentionParser()
        );

        int normalized = job.run();

        assertThat(normalized).isEqualTo(1);
        assertThat(row.getNormalizedAt()).isNotNull();
        assertThat(row.getNormalizationSkippedAt()).isNull();
        verify(mentionRepository).save(any(GdeltMention.class));
    }

    @Test
    void skipsInvalidRows() {
        GdeltMentionRepository mentionRepository = mock(GdeltMentionRepository.class);
        StagingRow row = stagingRow("invalid");

        when(mentionRepository.findUnnormalizedRows("MENTIONS")).thenReturn(List.of(row));

        GdeltMentionNormalizationJob job = new GdeltMentionNormalizationJob(
                mentionRepository,
                new GdeltMentionParser()
        );

        int normalized = job.run();

        assertThat(normalized).isZero();
        assertThat(row.getNormalizedAt()).isNull();
        assertThat(row.getNormalizationSkippedAt()).isNotNull();
        verify(mentionRepository, never()).save(any(GdeltMention.class));
    }

    @Test
    void marksRowsWithExistingNormalizedMentionAsNormalized() {
        GdeltMentionRepository mentionRepository = mock(GdeltMentionRepository.class);
        StagingRow row = stagingRow(GdeltMentionParserTests.mentionLine());

        when(mentionRepository.findUnnormalizedRows("MENTIONS")).thenReturn(List.of(row));
        when(mentionRepository.existsByStagingRow(row)).thenReturn(true);

        GdeltMentionNormalizationJob job = new GdeltMentionNormalizationJob(
                mentionRepository,
                new GdeltMentionParser()
        );

        int normalized = job.run();

        assertThat(normalized).isEqualTo(1);
        assertThat(row.getNormalizedAt()).isNotNull();
        assertThat(row.getNormalizationSkippedAt()).isNull();
        verify(mentionRepository, never()).save(any(GdeltMention.class));
    }

    private StagingRow stagingRow(String rawLine) {
        SourceBatch batch = new SourceBatch("GDELT", "20260509133000");
        batch.putFile("MENTIONS", "http://example.test/mentions.zip", 1, "abc");
        RawSourceFile file = batch.findFile("MENTIONS").orElseThrow();
        return new StagingRow(batch, file, 1, rawLine);
    }
}
