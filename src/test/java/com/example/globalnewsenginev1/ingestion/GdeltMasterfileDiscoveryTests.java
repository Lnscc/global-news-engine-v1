package com.example.globalnewsenginev1.ingestion;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GdeltMasterfileDiscoveryTests {

    private final GdeltMasterfileDiscovery discovery =
            new GdeltMasterfileDiscovery(HttpClient.newHttpClient(), URI.create("http://localhost/masterfilelist.txt"));

    @Test
    void returnsOnlyCompleteWindowsSortedByTimestamp() {
        String masterfile = """
                10 abc http://data.gdeltproject.org/gdeltv2/20260605120000.export.CSV.zip
                11 def http://data.gdeltproject.org/gdeltv2/20260605120000.mentions.CSV.zip
                12 ghi http://data.gdeltproject.org/gdeltv2/20260605120000.gkg.csv.zip
                13 jkl http://data.gdeltproject.org/gdeltv2/20260605121500.export.CSV.zip
                14 mno http://data.gdeltproject.org/gdeltv2/20260605123000.gkg.csv.zip
                15 pqr http://data.gdeltproject.org/gdeltv2/20260605113000.gkg.csv.zip
                16 stu http://data.gdeltproject.org/gdeltv2/20260605113000.mentions.CSV.zip
                17 vwx http://data.gdeltproject.org/gdeltv2/20260605113000.export.CSV.zip
                18 ignored http://data.gdeltproject.org/gdeltv2/20260605120000.translation.export.CSV.zip
                """;

        List<GdeltCompleteWindow> windows = discovery.parseCompleteWindows(masterfile);

        assertThat(windows).extracting(GdeltCompleteWindow::sourceTimestamp)
                .containsExactly(
                        Instant.parse("2026-06-05T11:30:00Z"),
                        Instant.parse("2026-06-05T12:00:00Z"));
        assertThat(windows).allSatisfy(window ->
                assertThat(window.files().keySet()).containsExactlyInAnyOrder(GdeltDataset.values()));
    }

    @Test
    void ignoresBlankMalformedAndNonGdeltLines() {
        String masterfile = """

                malformed
                10 abc http://data.gdeltproject.org/gdeltv2/20260605120000.export.CSV.zip
                11 def http://data.gdeltproject.org/gdeltv2/not-a-gdelt-file.zip
                12 ghi http://data.gdeltproject.org/gdeltv2/20260605120000.gkg.CSV.zip
                """;

        assertThat(discovery.parseCompleteWindows(masterfile)).isEmpty();
    }
}
