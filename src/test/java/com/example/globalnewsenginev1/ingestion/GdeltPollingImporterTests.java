package com.example.globalnewsenginev1.ingestion;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GdeltPollingImporterTests {

    @Test
    void continuesAfterOneWindowFails() {
        GdeltMasterfileDiscovery discovery = mock(GdeltMasterfileDiscovery.class);
        GdeltRawImporter importer = mock(GdeltRawImporter.class);
        Instant firstTimestamp = Instant.parse("2026-06-05T12:00:00Z");
        Instant secondTimestamp = Instant.parse("2026-06-05T12:15:00Z");

        when(discovery.discoverLatestCompleteWindows(2)).thenReturn(List.of(
                window(firstTimestamp),
                window(secondTimestamp)));
        when(importer.importWindow(firstTimestamp)).thenThrow(new IllegalStateException("download failed"));
        when(importer.importWindow(secondTimestamp)).thenReturn(List.of(
                new GdeltImportResult("20260605121500.export.CSV.zip", 1, false),
                new GdeltImportResult("20260605121500.mentions.CSV.zip", 1, false),
                new GdeltImportResult("20260605121500.gkg.csv.zip", 1, false)));

        GdeltPollingImporter pollingImporter = new GdeltPollingImporter(discovery, importer, 2);

        assertThatNoException().isThrownBy(pollingImporter::importLatestCompleteWindows);
        verify(importer).importWindow(firstTimestamp);
        verify(importer).importWindow(secondTimestamp);
    }

    private GdeltCompleteWindow window(Instant timestamp) {
        Map<GdeltDataset, URI> files = new EnumMap<>(GdeltDataset.class);
        for (GdeltDataset dataset : GdeltDataset.values()) {
            files.put(dataset, URI.create("http://localhost/" + timestamp + "/" + dataset.fileSuffix()));
        }
        return new GdeltCompleteWindow(timestamp, files);
    }
}
