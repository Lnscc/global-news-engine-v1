package com.example.globalnewsenginev1.gdelt;

import com.example.globalnewsenginev1.ingestion.IngestionStatus;
import com.example.globalnewsenginev1.ingestion.RawFileDownloader;
import com.example.globalnewsenginev1.ingestion.RawFileStorage;
import com.example.globalnewsenginev1.ingestion.RawSourceFile;
import com.example.globalnewsenginev1.ingestion.SourceBatch;
import com.example.globalnewsenginev1.ingestion.SourceBatchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GdeltDownloadJobTests {

    @TempDir
    Path tempDir;

    @Test
    void downloadsNewestDiscoveredCompleteBatch() {
        SourceBatchRepository batchRepository = mock(SourceBatchRepository.class);
        RawFileDownloader downloader = mock(RawFileDownloader.class);
        SourceBatch batch = completeBatch("20260506123000");

        when(batchRepository.findTop10BySourceAndStatusOrderByExternalBatchIdDesc("GDELT", IngestionStatus.DISCOVERED))
                .thenReturn(List.of(batch));

        GdeltDownloadJob job = new GdeltDownloadJob(
                batchRepository,
                downloader,
                new RawFileStorage(tempDir)
        );

        boolean downloaded = job.runNextBatch();

        assertThat(downloaded).isTrue();
        assertThat(batch.getStatus()).isEqualTo(IngestionStatus.DOWNLOADED);
        assertThat(batch.getFiles())
                .extracting(RawSourceFile::getStatus)
                .containsOnly(IngestionStatus.DOWNLOADED);
        assertThat(batch.getFiles())
                .extracting(RawSourceFile::getLocalPath)
                .allSatisfy(path -> assertThat(path).contains("20260506123000"));
        verify(batchRepository).save(batch);
    }

    @Test
    void skipsIncompleteDiscoveredBatches() {
        SourceBatchRepository batchRepository = mock(SourceBatchRepository.class);
        RawFileDownloader downloader = mock(RawFileDownloader.class);
        SourceBatch incompleteBatch = new SourceBatch("GDELT", "20260506123000");
        incompleteBatch.putFile("EVENTS", "http://example.test/events.zip", 1, "abc");

        when(batchRepository.findTop10BySourceAndStatusOrderByExternalBatchIdDesc("GDELT", IngestionStatus.DISCOVERED))
                .thenReturn(List.of(incompleteBatch));

        GdeltDownloadJob job = new GdeltDownloadJob(
                batchRepository,
                downloader,
                new RawFileStorage(tempDir)
        );

        boolean downloaded = job.runNextBatch();

        assertThat(downloaded).isFalse();
        assertThat(incompleteBatch.getStatus()).isEqualTo(IngestionStatus.DISCOVERED);
    }

    @Test
    void marksBatchFailedWhenAFileDownloadFails() throws IOException, InterruptedException {
        SourceBatchRepository batchRepository = mock(SourceBatchRepository.class);
        RawFileDownloader downloader = mock(RawFileDownloader.class);
        SourceBatch batch = completeBatch("20260506123000");

        when(batchRepository.findTop10BySourceAndStatusOrderByExternalBatchIdDesc("GDELT", IngestionStatus.DISCOVERED))
                .thenReturn(List.of(batch));
        doThrow(new IOException("network unavailable"))
                .when(downloader)
                .download(eq("http://example.test/events.zip"), any(Path.class));

        GdeltDownloadJob job = new GdeltDownloadJob(
                batchRepository,
                downloader,
                new RawFileStorage(tempDir)
        );

        boolean downloaded = job.runNextBatch();

        assertThat(downloaded).isFalse();
        assertThat(batch.getStatus()).isEqualTo(IngestionStatus.FAILED);
        assertThat(batch.findFile("EVENTS"))
                .map(RawSourceFile::getStatus)
                .hasValue(IngestionStatus.FAILED);
        assertThat(batch.findFile("EVENTS"))
                .map(RawSourceFile::getErrorMessage)
                .isEqualTo(Optional.of("network unavailable"));
        verify(batchRepository).save(batch);
    }

    private SourceBatch completeBatch(String timestamp) {
        SourceBatch batch = new SourceBatch("GDELT", timestamp);
        batch.putFile("EVENTS", "http://example.test/events.zip", 1, "abc");
        batch.putFile("MENTIONS", "http://example.test/mentions.zip", 2, "def");
        batch.putFile("GKG", "http://example.test/gkg.zip", 3, "ghi");
        return batch;
    }
}
