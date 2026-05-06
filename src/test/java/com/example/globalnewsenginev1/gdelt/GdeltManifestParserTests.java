package com.example.globalnewsenginev1.gdelt;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GdeltManifestParserTests {

    private final GdeltManifestParser parser = new GdeltManifestParser();

    @Test
    void parsesGdeltV2EventsMentionsAndGkgEntries() {
        String manifest = """
                123 abc http://data.gdeltproject.org/gdeltv2/20260506123000.export.CSV.zip
                456 def http://data.gdeltproject.org/gdeltv2/20260506123000.mentions.CSV.zip
                789 ghi http://data.gdeltproject.org/gdeltv2/20260506123000.gkg.csv.zip
                """;

        List<GdeltManifestEntry> entries = parser.parse(manifest);

        assertThat(entries).hasSize(3);
        assertThat(entries)
                .extracting(GdeltManifestEntry::fileType)
                .containsExactly(GdeltFileType.EVENTS, GdeltFileType.MENTIONS, GdeltFileType.GKG);
        assertThat(entries)
                .allSatisfy(entry -> assertThat(entry.batchTimestamp()).isEqualTo("20260506123000"));
    }

    @Test
    void ignoresMalformedAndUnsupportedManifestLines() {
        String manifest = """
                not-a-valid-line
                123 abc http://data.gdeltproject.org/gdeltv2/20260506123000.translation.export.CSV.zip
                nope abc http://data.gdeltproject.org/gdeltv2/20260506123000.export.CSV.zip
                123 abc http://data.gdeltproject.org/gdeltv2/not-a-date.export.CSV.zip
                456 def http://data.gdeltproject.org/gdeltv2/20260506124500.mentions.CSV.zip
                """;

        List<GdeltManifestEntry> entries = parser.parse(manifest);

        assertThat(entries).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.batchTimestamp()).isEqualTo("20260506124500");
                    assertThat(entry.fileType()).isEqualTo(GdeltFileType.MENTIONS);
                    assertThat(entry.sizeBytes()).isEqualTo(456);
                });
    }
}
