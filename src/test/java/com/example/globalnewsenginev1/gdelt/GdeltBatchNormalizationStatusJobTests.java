package com.example.globalnewsenginev1.gdelt;

import com.example.globalnewsenginev1.ingestion.IngestionStatus;
import com.example.globalnewsenginev1.ingestion.SourceBatch;
import com.example.globalnewsenginev1.ingestion.SourceBatchRepository;
import com.example.globalnewsenginev1.ingestion.StagingRowRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GdeltBatchNormalizationStatusJobTests {

    @Test
    void marksParsedBatchNormalizedWhenAllStagedRowsAreNormalized() {
        SourceBatchRepository batchRepository = mock(SourceBatchRepository.class);
        StagingRowRepository stagingRowRepository = mock(StagingRowRepository.class);
        GdeltEventRepository eventRepository = mock(GdeltEventRepository.class);
        GdeltMentionRepository mentionRepository = mock(GdeltMentionRepository.class);
        GdeltGkgRepository gkgRepository = mock(GdeltGkgRepository.class);
        SourceBatch batch = parsedBatch();

        when(batchRepository.findTop10BySourceAndStatusInOrderByExternalBatchIdDesc(
                "GDELT",
                List.of(IngestionStatus.PARSED, IngestionStatus.NORMALIZING)
        )).thenReturn(List.of(batch));
        when(stagingRowRepository.countBySourceBatch(batch)).thenReturn(6L);
        when(stagingRowRepository.countUnhandledBySourceBatchAndFileType(batch, "EVENTS")).thenReturn(0L);
        when(stagingRowRepository.countUnhandledBySourceBatchAndFileType(batch, "MENTIONS")).thenReturn(0L);
        when(stagingRowRepository.countUnhandledBySourceBatchAndFileType(batch, "GKG")).thenReturn(0L);

        GdeltBatchNormalizationStatusJob job = new GdeltBatchNormalizationStatusJob(
                batchRepository,
                stagingRowRepository,
                eventRepository,
                mentionRepository,
                gkgRepository
        );

        int updated = job.run();

        assertThat(updated).isEqualTo(1);
        assertThat(batch.getStatus()).isEqualTo(IngestionStatus.NORMALIZED);
        verify(batchRepository).save(batch);
    }

    @Test
    void marksParsedBatchNormalizingWhenSomeRowsAreMissing() {
        SourceBatchRepository batchRepository = mock(SourceBatchRepository.class);
        StagingRowRepository stagingRowRepository = mock(StagingRowRepository.class);
        GdeltEventRepository eventRepository = mock(GdeltEventRepository.class);
        GdeltMentionRepository mentionRepository = mock(GdeltMentionRepository.class);
        GdeltGkgRepository gkgRepository = mock(GdeltGkgRepository.class);
        SourceBatch batch = parsedBatch();

        when(batchRepository.findTop10BySourceAndStatusInOrderByExternalBatchIdDesc(
                "GDELT",
                List.of(IngestionStatus.PARSED, IngestionStatus.NORMALIZING)
        )).thenReturn(List.of(batch));
        when(stagingRowRepository.countBySourceBatch(batch)).thenReturn(6L);
        when(stagingRowRepository.countUnhandledBySourceBatchAndFileType(batch, "EVENTS")).thenReturn(0L);
        when(stagingRowRepository.countUnhandledBySourceBatchAndFileType(batch, "MENTIONS")).thenReturn(1L);
        when(stagingRowRepository.countUnhandledBySourceBatchAndFileType(batch, "GKG")).thenReturn(0L);

        GdeltBatchNormalizationStatusJob job = new GdeltBatchNormalizationStatusJob(
                batchRepository,
                stagingRowRepository,
                eventRepository,
                mentionRepository,
                gkgRepository
        );

        int updated = job.run();

        assertThat(updated).isEqualTo(1);
        assertThat(batch.getStatus()).isEqualTo(IngestionStatus.NORMALIZING);
        verify(batchRepository).save(batch);
    }

    private SourceBatch parsedBatch() {
        SourceBatch batch = new SourceBatch("GDELT", "20260509133000");
        batch.markDownloaded();
        batch.markParsing();
        batch.markParsed();
        return batch;
    }
}
