package com.example.globalnewsenginev1.gdelt;

import com.example.globalnewsenginev1.ingestion.RawSourceFile;
import com.example.globalnewsenginev1.ingestion.SourceBatch;
import com.example.globalnewsenginev1.ingestion.StagingRow;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GdeltGkgNormalizationJobTests {

    @Test
    void normalizesUnprocessedGkgRows() {
        GdeltGkgRepository gkgRepository = mock(GdeltGkgRepository.class);
        StagingRow row = stagingRow(GdeltGkgParserTests.gkgLine());

        when(gkgRepository.findUnnormalizedRows(eq("GKG"), any(Pageable.class))).thenReturn(List.of(row));
        when(gkgRepository.findByGkgRecordId("20260509133000-1")).thenReturn(Optional.empty());
        when(gkgRepository.save(any(GdeltGkg.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GdeltGkgNormalizationJob job = new GdeltGkgNormalizationJob(
                gkgRepository,
                new GdeltGkgParser(),
                1000
        );

        int normalized = job.run();

        assertThat(normalized).isEqualTo(1);
        assertThat(row.getNormalizedAt()).isNotNull();
        assertThat(row.getNormalizationSkippedAt()).isNull();
        verify(gkgRepository).save(any(GdeltGkg.class));
    }

    @Test
    void skipsInvalidRows() {
        GdeltGkgRepository gkgRepository = mock(GdeltGkgRepository.class);
        StagingRow row = stagingRow("invalid");

        when(gkgRepository.findUnnormalizedRows(eq("GKG"), any(Pageable.class))).thenReturn(List.of(row));

        GdeltGkgNormalizationJob job = new GdeltGkgNormalizationJob(
                gkgRepository,
                new GdeltGkgParser(),
                1000
        );

        int normalized = job.run();

        assertThat(normalized).isZero();
        assertThat(row.getNormalizedAt()).isNull();
        assertThat(row.getNormalizationSkippedAt()).isNotNull();
        verify(gkgRepository, never()).save(any(GdeltGkg.class));
    }

    @Test
    void marksRowsWithExistingNormalizedGkgAsNormalized() {
        GdeltGkgRepository gkgRepository = mock(GdeltGkgRepository.class);
        StagingRow row = stagingRow(GdeltGkgParserTests.gkgLine());

        when(gkgRepository.findUnnormalizedRows(eq("GKG"), any(Pageable.class))).thenReturn(List.of(row));
        when(gkgRepository.existsByStagingRow(row)).thenReturn(true);

        GdeltGkgNormalizationJob job = new GdeltGkgNormalizationJob(
                gkgRepository,
                new GdeltGkgParser(),
                1000
        );

        int normalized = job.run();

        assertThat(normalized).isEqualTo(1);
        assertThat(row.getNormalizedAt()).isNotNull();
        assertThat(row.getNormalizationSkippedAt()).isNull();
        verify(gkgRepository, never()).save(any(GdeltGkg.class));
    }

    private StagingRow stagingRow(String rawLine) {
        SourceBatch batch = new SourceBatch("GDELT", "20260509133000");
        batch.putFile("GKG", "http://example.test/gkg.zip", 1, "abc");
        RawSourceFile file = batch.findFile("GKG").orElseThrow();
        return new StagingRow(batch, file, 1, rawLine);
    }
}
