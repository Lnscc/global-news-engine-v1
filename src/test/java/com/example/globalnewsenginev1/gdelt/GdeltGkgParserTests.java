package com.example.globalnewsenginev1.gdelt;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GdeltGkgParserTests {

    private final GdeltGkgParser parser = new GdeltGkgParser();

    @Test
    void parsesGkgLineIntoRecord() {
        Optional<GdeltGkgRecord> record = parser.parse(gkgLine());

        assertThat(record).isPresent();
        assertThat(record.orElseThrow().gkgRecordId()).isEqualTo("20260509133000-1");
        assertThat(record.orElseThrow().date()).isEqualTo(LocalDateTime.of(2026, 5, 9, 13, 30));
        assertThat(record.orElseThrow().sourceCollectionIdentifier()).isEqualTo(1);
        assertThat(record.orElseThrow().sourceCommonName()).isEqualTo("example.test");
        assertThat(record.orElseThrow().documentIdentifier()).isEqualTo("https://example.test/news");
        assertThat(record.orElseThrow().themes()).isEqualTo("WB_678_DIGITAL_GOVERNMENT;TAX_FNCACT_PRESIDENT");
        assertThat(record.orElseThrow().persons()).isEqualTo("Jane Doe;John Smith");
        assertThat(record.orElseThrow().organizations()).isEqualTo("Example Org");
        assertThat(record.orElseThrow().tone()).isEqualByComparingTo(new BigDecimal("-2.5"));
        assertThat(record.orElseThrow().positiveScore()).isEqualByComparingTo(new BigDecimal("1.0"));
        assertThat(record.orElseThrow().wordCount()).isEqualByComparingTo(new BigDecimal("850"));
    }

    @Test
    void rejectsLinesWithTooFewColumns() {
        assertThat(parser.parse("id\t20260509133000")).isEmpty();
    }

    static String gkgLine() {
        String[] columns = new String[27];
        for (int i = 0; i < columns.length; i++) {
            columns[i] = "";
        }
        columns[0] = "20260509133000-1";
        columns[1] = "20260509133000";
        columns[2] = "1";
        columns[3] = "example.test";
        columns[4] = "https://example.test/news";
        columns[5] = "AFFECT#5#crisis#1";
        columns[6] = "AFFECT#5#crisis#1";
        columns[7] = "WB_678_DIGITAL_GOVERNMENT;TAX_FNCACT_PRESIDENT";
        columns[8] = "WB_678_DIGITAL_GOVERNMENT,123;TAX_FNCACT_PRESIDENT,456";
        columns[9] = "4#Berlin#GM#GM16#52.5167#13.3833#-1746443";
        columns[10] = "4#Berlin#GM#GM16#52.5167#13.3833#-1746443";
        columns[11] = "Jane Doe;John Smith";
        columns[12] = "Jane Doe,120;John Smith,240";
        columns[13] = "Example Org";
        columns[14] = "Example Org,360";
        columns[15] = "-2.5,1.0,3.5,4.5,10.0,0.5,850";
        columns[16] = "20260509";
        columns[17] = "wc:850,c1.1:2.0";
        columns[18] = "https://example.test/image.jpg";
        columns[19] = "https://example.test/related.jpg";
        columns[20] = "https://example.test/social.jpg";
        columns[21] = "https://example.test/video";
        columns[22] = "120|quote";
        columns[23] = "Berlin,12";
        columns[24] = "5,protest";
        columns[25] = "srclc:eng";
        columns[26] = "<PAGE_TITLE>Example</PAGE_TITLE>";
        return String.join("\t", columns);
    }
}
