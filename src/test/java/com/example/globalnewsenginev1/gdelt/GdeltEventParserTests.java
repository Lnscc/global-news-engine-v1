package com.example.globalnewsenginev1.gdelt;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GdeltEventParserTests {

    private final GdeltEventParser parser = new GdeltEventParser();

    @Test
    void parsesGdeltEventLineIntoRecord() {
        Optional<GdeltEventRecord> record = parser.parse(eventLine());

        assertThat(record).isPresent();
        assertThat(record.orElseThrow().globalEventId()).isEqualTo(123456789L);
        assertThat(record.orElseThrow().eventDate()).isEqualTo(LocalDate.of(2026, 5, 6));
        assertThat(record.orElseThrow().actor1Name()).isEqualTo("GERMANY");
        assertThat(record.orElseThrow().actor2Name()).isEqualTo("FRANCE");
        assertThat(record.orElseThrow().rootEvent()).isTrue();
        assertThat(record.orElseThrow().eventCode()).isEqualTo("042");
        assertThat(record.orElseThrow().goldsteinScale()).isEqualByComparingTo(new BigDecimal("1.9"));
        assertThat(record.orElseThrow().averageTone()).isEqualByComparingTo(new BigDecimal("-2.5"));
        assertThat(record.orElseThrow().actionGeoFullName()).isEqualTo("Berlin, Berlin, Germany");
        assertThat(record.orElseThrow().actionGeoLatitude()).isEqualByComparingTo(new BigDecimal("52.5167"));
        assertThat(record.orElseThrow().dateAdded()).isEqualTo(LocalDateTime.of(2026, 5, 6, 12, 30));
        assertThat(record.orElseThrow().sourceUrl()).isEqualTo("https://example.test/news");
    }

    @Test
    void rejectsLinesWithTooFewColumns() {
        assertThat(parser.parse("123\t20260506")).isEmpty();
    }

    static String eventLine() {
        String[] columns = new String[61];
        for (int i = 0; i < columns.length; i++) {
            columns[i] = "";
        }
        columns[0] = "123456789";
        columns[1] = "20260506";
        columns[2] = "202605";
        columns[3] = "2026";
        columns[4] = "2026.3452";
        columns[5] = "DEU";
        columns[6] = "GERMANY";
        columns[7] = "GM";
        columns[15] = "FRA";
        columns[16] = "FRANCE";
        columns[17] = "FR";
        columns[25] = "1";
        columns[26] = "042";
        columns[27] = "04";
        columns[28] = "04";
        columns[29] = "1";
        columns[30] = "1.9";
        columns[31] = "4";
        columns[32] = "2";
        columns[33] = "3";
        columns[34] = "-2.5";
        columns[52] = "Berlin, Berlin, Germany";
        columns[53] = "GM";
        columns[56] = "52.5167";
        columns[57] = "13.3833";
        columns[59] = "20260506123000";
        columns[60] = "https://example.test/news";
        return String.join("\t", columns);
    }
}
