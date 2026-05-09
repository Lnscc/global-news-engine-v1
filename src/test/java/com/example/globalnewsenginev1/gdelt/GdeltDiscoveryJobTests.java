package com.example.globalnewsenginev1.gdelt;

import com.example.globalnewsenginev1.ingestion.IngestionStatus;
import com.example.globalnewsenginev1.ingestion.RawSourceFile;
import com.example.globalnewsenginev1.ingestion.SourceBatch;
import com.example.globalnewsenginev1.ingestion.SourceBatchRepository;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GdeltDiscoveryJobTests {

    @Test
    void discoversAndUpdatesBatchesFromManifest() throws IOException, InterruptedException {
        GdeltManifestClient manifestClient = mock(GdeltManifestClient.class);
        SourceBatchRepository batchRepository = mock(SourceBatchRepository.class);
        when(manifestClient.fetchMasterFileList()).thenReturn("""
                123 abc http://data.gdeltproject.org/gdeltv2/20260506123000.export.CSV.zip
                456 def http://data.gdeltproject.org/gdeltv2/20260506123000.mentions.CSV.zip
                789 ghi http://data.gdeltproject.org/gdeltv2/20260506123000.gkg.csv.zip
                111 jkl http://data.gdeltproject.org/gdeltv2/20260506124500.export.CSV.zip
                """);
        when(batchRepository.findBySourceAndExternalBatchId(anyString(), anyString())).thenReturn(Optional.empty());
        when(batchRepository.save(any(SourceBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        GdeltDiscoveryJob job = new GdeltDiscoveryJob(
                manifestClient,
                new GdeltManifestParser(),
                batchRepository,
                96,
                Duration.ZERO,
                Clock.systemUTC()
        );

        int discovered = job.run();

        assertThat(discovered).isEqualTo(2);
        verify(batchRepository).save(argThat(batch ->
                batch.getSource().equals("GDELT")
                        && batch.getExternalBatchId().equals("20260506123000")
                        && hasFileEnding(batch, "EVENTS", "20260506123000.export.CSV.zip")
                        && hasFileEnding(batch, "MENTIONS", "20260506123000.mentions.CSV.zip")
                        && hasFileEnding(batch, "GKG", "20260506123000.gkg.csv.zip")
                        && batch.getStatus() == IngestionStatus.DISCOVERED
        ));
        verify(batchRepository).save(argThat(batch ->
                batch.getExternalBatchId().equals("20260506124500")
                        && hasFileSize(batch, "EVENTS", 111L)
                        && batch.findFile("MENTIONS").isEmpty()
                        && batch.findFile("GKG").isEmpty()
        ));
    }

    @Test
    void updatesExistingBatchInsteadOfCreatingNewOne() throws IOException, InterruptedException {
        GdeltManifestClient manifestClient = mock(GdeltManifestClient.class);
        SourceBatchRepository batchRepository = mock(SourceBatchRepository.class);
        SourceBatch existingBatch = new SourceBatch("GDELT", "20260506123000");

        when(manifestClient.fetchMasterFileList()).thenReturn("""
                123 abc http://data.gdeltproject.org/gdeltv2/20260506123000.export.CSV.zip
                456 def http://data.gdeltproject.org/gdeltv2/20260506123000.mentions.CSV.zip
                """);
        when(batchRepository.findBySourceAndExternalBatchId("GDELT", "20260506123000"))
                .thenReturn(Optional.of(existingBatch));

        GdeltDiscoveryJob job = new GdeltDiscoveryJob(
                manifestClient,
                new GdeltManifestParser(),
                batchRepository,
                96,
                Duration.ZERO,
                Clock.systemUTC()
        );

        int discovered = job.run();

        assertThat(discovered).isEqualTo(1);
        verify(batchRepository).save(existingBatch);
        assertThat(existingBatch.findFile("EVENTS")).map(RawSourceFile::getSizeBytes).hasValue(123L);
        assertThat(existingBatch.findFile("MENTIONS")).map(RawSourceFile::getSizeBytes).hasValue(456L);
    }

    @Test
    void limitsDiscoveryToNewestBatches() throws IOException, InterruptedException {
        GdeltManifestClient manifestClient = mock(GdeltManifestClient.class);
        SourceBatchRepository batchRepository = mock(SourceBatchRepository.class);
        when(manifestClient.fetchMasterFileList()).thenReturn("""
                123 abc http://data.gdeltproject.org/gdeltv2/20260506120000.export.CSV.zip
                456 def http://data.gdeltproject.org/gdeltv2/20260506121500.export.CSV.zip
                789 ghi http://data.gdeltproject.org/gdeltv2/20260506123000.export.CSV.zip
                """);
        when(batchRepository.findBySourceAndExternalBatchId(anyString(), anyString())).thenReturn(Optional.empty());

        GdeltDiscoveryJob job = new GdeltDiscoveryJob(
                manifestClient,
                new GdeltManifestParser(),
                batchRepository,
                2,
                Duration.ZERO,
                Clock.systemUTC()
        );

        int discovered = job.run();

        assertThat(discovered).isEqualTo(2);
        verify(batchRepository).save(argThat(batch -> batch.getExternalBatchId().equals("20260506123000")));
        verify(batchRepository).save(argThat(batch -> batch.getExternalBatchId().equals("20260506121500")));
    }

    @Test
    void skipsBatchesNewerThanMinimumAge() throws IOException, InterruptedException {
        GdeltManifestClient manifestClient = mock(GdeltManifestClient.class);
        SourceBatchRepository batchRepository = mock(SourceBatchRepository.class);
        when(manifestClient.fetchMasterFileList()).thenReturn("""
                123 abc http://data.gdeltproject.org/gdeltv2/20260509133000.export.CSV.zip
                456 def http://data.gdeltproject.org/gdeltv2/20260509134500.export.CSV.zip
                """);
        when(batchRepository.findBySourceAndExternalBatchId(anyString(), anyString())).thenReturn(Optional.empty());

        Clock clock = Clock.fixed(Instant.parse("2026-05-09T13:46:00Z"), ZoneOffset.UTC);
        GdeltDiscoveryJob job = new GdeltDiscoveryJob(
                manifestClient,
                new GdeltManifestParser(),
                batchRepository,
                2,
                Duration.ofMinutes(10),
                clock
        );

        int discovered = job.run();

        assertThat(discovered).isEqualTo(1);
        verify(batchRepository).save(argThat(batch -> batch.getExternalBatchId().equals("20260509133000")));
    }

    private boolean hasFileEnding(SourceBatch batch, String fileType, String urlEnding) {
        return batch.findFile(fileType)
                .map(RawSourceFile::getUrl)
                .filter(url -> url.endsWith(urlEnding))
                .isPresent();
    }

    private boolean hasFileSize(SourceBatch batch, String fileType, long sizeBytes) {
        return batch.findFile(fileType)
                .map(RawSourceFile::getSizeBytes)
                .filter(size -> size == sizeBytes)
                .isPresent();
    }
}
