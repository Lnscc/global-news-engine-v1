package com.example.globalnewsenginev1.gdelt;

import com.example.globalnewsenginev1.ingestion.IngestionStatus;
import com.example.globalnewsenginev1.ingestion.RawSourceFile;
import com.example.globalnewsenginev1.ingestion.RawZipLineReader;
import com.example.globalnewsenginev1.ingestion.SourceBatch;
import com.example.globalnewsenginev1.ingestion.SourceBatchRepository;
import com.example.globalnewsenginev1.ingestion.StagingRow;
import com.example.globalnewsenginev1.ingestion.StagingRowRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GdeltParseJobTests {

    @TempDir
    Path tempDir;

    @Test
    void parsesDownloadedBatchIntoStagingRows() throws IOException {
        SourceBatchRepository batchRepository = mock(SourceBatchRepository.class);
        StagingRowRepository stagingRowRepository = mock(StagingRowRepository.class);
        SourceBatch batch = downloadedBatch("20260506123000");

        when(batchRepository.findTop10BySourceAndStatusOrderByExternalBatchIdDesc("GDELT", IngestionStatus.DOWNLOADED))
                .thenReturn(List.of(batch));
        when(stagingRowRepository.existsByRawSourceFileAndLineNumber(any(RawSourceFile.class), any(Long.class)))
                .thenReturn(false);
        when(stagingRowRepository.save(any(StagingRow.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GdeltParseJob job = new GdeltParseJob(
                batchRepository,
                stagingRowRepository,
                new RawZipLineReader()
        );

        boolean parsed = job.runNextBatch();

        assertThat(parsed).isTrue();
        assertThat(batch.getStatus()).isEqualTo(IngestionStatus.PARSED);
        verify(stagingRowRepository, org.mockito.Mockito.times(9)).save(any(StagingRow.class));
        verify(batchRepository).save(batch);
    }

    @Test
    void skipsWhenNoDownloadedBatchIsReady() {
        SourceBatchRepository batchRepository = mock(SourceBatchRepository.class);
        StagingRowRepository stagingRowRepository = mock(StagingRowRepository.class);
        when(batchRepository.findTop10BySourceAndStatusOrderByExternalBatchIdDesc("GDELT", IngestionStatus.DOWNLOADED))
                .thenReturn(List.of());

        GdeltParseJob job = new GdeltParseJob(
                batchRepository,
                stagingRowRepository,
                new RawZipLineReader()
        );

        boolean parsed = job.runNextBatch();

        assertThat(parsed).isFalse();
    }

    @Test
    void marksBatchFailedWhenZipCannotBeRead() {
        SourceBatchRepository batchRepository = mock(SourceBatchRepository.class);
        StagingRowRepository stagingRowRepository = mock(StagingRowRepository.class);
        SourceBatch batch = new SourceBatch("GDELT", "20260506123000");
        batch.putFile("EVENTS", "http://example.test/events.zip", 1, "abc");
        batch.putFile("MENTIONS", "http://example.test/mentions.zip", 1, "def");
        batch.putFile("GKG", "http://example.test/gkg.zip", 1, "ghi");
        batch.findFile("EVENTS").orElseThrow().markDownloaded(tempDir.resolve("missing-events.zip").toString());
        batch.findFile("MENTIONS").orElseThrow().markDownloaded(tempDir.resolve("missing-mentions.zip").toString());
        batch.findFile("GKG").orElseThrow().markDownloaded(tempDir.resolve("missing-gkg.zip").toString());
        batch.markDownloaded();

        when(batchRepository.findTop10BySourceAndStatusOrderByExternalBatchIdDesc("GDELT", IngestionStatus.DOWNLOADED))
                .thenReturn(List.of(batch));

        GdeltParseJob job = new GdeltParseJob(
                batchRepository,
                stagingRowRepository,
                new RawZipLineReader()
        );

        boolean parsed = job.runNextBatch();

        assertThat(parsed).isFalse();
        assertThat(batch.getStatus()).isEqualTo(IngestionStatus.FAILED);
        verify(batchRepository).save(batch);
    }

    private SourceBatch downloadedBatch(String timestamp) throws IOException {
        SourceBatch batch = new SourceBatch("GDELT", timestamp);
        for (GdeltFileType fileType : GdeltFileType.values()) {
            batch.putFile(fileType.name(), "http://example.test/" + fileType.name() + ".zip", 1, "hash");
            Path zipPath = tempDir.resolve(fileType.name() + ".zip");
            writeZip(zipPath, fileType.name() + "-1\n" + fileType.name() + "-2\n" + fileType.name() + "-3\n");
            batch.findFile(fileType.name()).orElseThrow().markDownloaded(zipPath.toString());
        }
        batch.markDownloaded();
        return batch;
    }

    private void writeZip(Path zipPath, String content) throws IOException {
        Files.createDirectories(zipPath.getParent());
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipPath), StandardCharsets.UTF_8)) {
            zipOutputStream.putNextEntry(new ZipEntry("data.csv"));
            zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
        }
    }
}
