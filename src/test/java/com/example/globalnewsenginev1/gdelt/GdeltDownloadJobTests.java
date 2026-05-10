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
import static org.mockito.Mockito.never;
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

        when(batchRepository.findTop10BySourceAndStatusInOrderByExternalBatchIdDesc(
                "GDELT",
                List.of(IngestionStatus.DISCOVERED, IngestionStatus.FAILED)
        ))
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

        when(batchRepository.findTop10BySourceAndStatusInOrderByExternalBatchIdDesc(
                "GDELT",
                List.of(IngestionStatus.DISCOVERED, IngestionStatus.FAILED)
        ))
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

        when(batchRepository.findTop10BySourceAndStatusInOrderByExternalBatchIdDesc(
                "GDELT",
                List.of(IngestionStatus.DISCOVERED, IngestionStatus.FAILED)
        ))
                .thenReturn(List.of(batch));
        doThrow(new IOException("network unavailable"))
                .when(downloader)
                .download(eq("http://example.test/20260506123000/events.zip"), any(Path.class));

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

    @Test
    void triesOlderCandidateWhenNewestDownloadFails() throws IOException, InterruptedException {
        SourceBatchRepository batchRepository = mock(SourceBatchRepository.class);
        RawFileDownloader downloader = mock(RawFileDownloader.class);
        SourceBatch newestBatch = completeBatch("20260506124500");
        SourceBatch olderBatch = completeBatch("20260506123000");

        when(batchRepository.findTop10BySourceAndStatusInOrderByExternalBatchIdDesc(
                "GDELT",
                List.of(IngestionStatus.DISCOVERED, IngestionStatus.FAILED)
        ))
                .thenReturn(List.of(newestBatch, olderBatch));
        doThrow(new IOException("temporary 404"))
                .when(downloader)
                .download(eq("http://example.test/20260506124500/gkg.zip"), any(Path.class));

        GdeltDownloadJob job = new GdeltDownloadJob(
                batchRepository,
                downloader,
                new RawFileStorage(tempDir)
        );

        boolean downloaded = job.runNextBatch();

        assertThat(downloaded).isTrue();
        assertThat(newestBatch.getStatus()).isEqualTo(IngestionStatus.FAILED);
        assertThat(olderBatch.getStatus()).isEqualTo(IngestionStatus.DOWNLOADED);
        verify(batchRepository).save(newestBatch);
        verify(batchRepository).save(olderBatch);
    }

    @Test
    void retriesFailedBatchAndReusesAlreadyDownloadedFiles() throws IOException, InterruptedException {
        SourceBatchRepository batchRepository = mock(SourceBatchRepository.class);
        RawFileDownloader downloader = mock(RawFileDownloader.class);
        SourceBatch batch = completeBatch("20260506123000");
        RawFileStorage storage = new RawFileStorage(tempDir);

        Path eventsPath = storage.pathFor(batch, batch.findFile("EVENTS").orElseThrow());
        java.nio.file.Files.createDirectories(eventsPath.getParent());
        java.nio.file.Files.writeString(eventsPath, "already downloaded");
        batch.findFile("EVENTS").orElseThrow().markDownloaded(eventsPath.toString());
        batch.findFile("MENTIONS").orElseThrow().markFailed("temporary 404");
        batch.findFile("GKG").orElseThrow().markFailed("temporary 404");
        batch.markFailed();

        when(batchRepository.findTop10BySourceAndStatusInOrderByExternalBatchIdDesc(
                "GDELT",
                List.of(IngestionStatus.DISCOVERED, IngestionStatus.FAILED)
        ))
                .thenReturn(List.of(batch));

        GdeltDownloadJob job = new GdeltDownloadJob(
                batchRepository,
                downloader,
                storage
        );

        boolean downloaded = job.runNextBatch();

        assertThat(downloaded).isTrue();
        assertThat(batch.getStatus()).isEqualTo(IngestionStatus.DOWNLOADED);
        verify(downloader, never()).download(eq("http://example.test/20260506123000/events.zip"), any(Path.class));
        verify(downloader).download(eq("http://example.test/20260506123000/mentions.zip"), any(Path.class));
        verify(downloader).download(eq("http://example.test/20260506123000/gkg.zip"), any(Path.class));
    }

    private SourceBatch completeBatch(String timestamp) {
        SourceBatch batch = new SourceBatch("GDELT", timestamp);
        batch.putFile("EVENTS", "http://example.test/" + timestamp + "/events.zip", 1, "abc");
        batch.putFile("MENTIONS", "http://example.test/" + timestamp + "/mentions.zip", 2, "def");
        batch.putFile("GKG", "http://example.test/" + timestamp + "/gkg.zip", 3, "ghi");
        return batch;
    }
}
