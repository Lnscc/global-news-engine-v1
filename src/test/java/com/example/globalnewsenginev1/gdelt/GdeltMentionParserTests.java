package com.example.globalnewsenginev1.gdelt;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GdeltMentionParserTests {

    private final GdeltMentionParser parser = new GdeltMentionParser();

    @Test
    void parsesMentionLineIntoRecord() {
        Optional<GdeltMentionRecord> record = parser.parse(mentionLine());

        assertThat(record).isPresent();
        assertThat(record.orElseThrow().globalEventId()).isEqualTo(123456789L);
        assertThat(record.orElseThrow().eventTimeDate()).isEqualTo(LocalDateTime.of(2026, 5, 9, 13, 30));
        assertThat(record.orElseThrow().mentionTimeDate()).isEqualTo(LocalDateTime.of(2026, 5, 9, 13, 31));
        assertThat(record.orElseThrow().mentionType()).isEqualTo(1);
        assertThat(record.orElseThrow().mentionSourceName()).isEqualTo("example.test");
        assertThat(record.orElseThrow().mentionIdentifier()).isEqualTo("https://example.test/news");
        assertThat(record.orElseThrow().inRawText()).isTrue();
        assertThat(record.orElseThrow().mentionDocTone()).isEqualByComparingTo(new BigDecimal("-1.25"));
    }

    @Test
    void rejectsLinesWithTooFewColumns() {
        assertThat(parser.parse("123\t20260509133000")).isEmpty();
    }

    static String mentionLine() {
        return String.join("\t",
                "123456789",
                "20260509133000",
                "20260509133100",
                "1",
                "example.test",
                "https://example.test/news",
                "4",
                "120",
                "240",
                "360",
                "1",
                "80",
                "850",
                "-1.25",
                "srclc:eng;eng:Human Translation",
                "extra-data"
        );
    }
}
