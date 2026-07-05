package com.example.globalnewsenginev1.gdelt.staging.parser;

import com.example.globalnewsenginev1.gdelt.staging.model.GdeltStageEvent;
import com.example.globalnewsenginev1.gdelt.staging.model.GdeltStageGkg;
import com.example.globalnewsenginev1.gdelt.staging.model.GdeltStageMention;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class GdeltParserTests {

    @Test
    void parsesCoreEventFieldsAndPreservesEmptyColumns() {
        GdeltStageEvent event = new GdeltEventParser().parse(eventRow());

        assertThat(event.globalEventId()).isEqualTo(123L);
        assertThat(event.eventDate()).isEqualTo(LocalDate.parse("2026-07-05"));
        assertThat(event.actor1Code()).isEqualTo("USA");
        assertThat(event.actor1Name()).isEqualTo("United States");
        assertThat(event.actor2Name()).isEqualTo("Russia");
        assertThat(event.eventCode()).isEqualTo("042");
        assertThat(event.quadClass()).isEqualTo(1);
        assertThat(event.goldsteinScale()).isEqualTo(-2.5);
        assertThat(event.avgTone()).isEqualTo(-1.25);
        assertThat(event.sourceUrl()).isEqualTo("https://example.org/a");
    }

    @Test
    void parsesCoreMentionFields() {
        GdeltStageMention mention = new GdeltMentionParser().parse(mentionRow());

        assertThat(mention.globalEventId()).isEqualTo(123L);
        assertThat(mention.eventTimeDate()).isEqualTo(Instant.parse("2026-07-05T12:00:00Z"));
        assertThat(mention.mentionTimeDate()).isEqualTo(Instant.parse("2026-07-05T12:15:00Z"));
        assertThat(mention.mentionSourceName()).isEqualTo("example.org");
        assertThat(mention.confidence()).isEqualTo(80);
        assertThat(mention.mentionDocTone()).isEqualTo(-1.5);
    }

    @Test
    void parsesCoreGkgFields() {
        GdeltStageGkg gkg = new GdeltGkgParser().parse(gkgRow());

        assertThat(gkg.gkgRecordId()).isEqualTo("20260705120000-1");
        assertThat(gkg.documentDate()).isEqualTo(Instant.parse("2026-07-05T12:00:00Z"));
        assertThat(gkg.sourceCommonName()).isEqualTo("example.org");
        assertThat(gkg.documentIdentifier()).isEqualTo("https://example.org/a");
        assertThat(gkg.themes()).isEqualTo("WB_133_INFORMATION_AND_COMMUNICATION_TECHNOLOGIES");
        assertThat(gkg.persons()).isEqualTo("Jane Doe");
        assertThat(gkg.organizations()).isEqualTo("Example Org");
        assertThat(gkg.locations()).isEqualTo("1#Berlin#GM#GM16#52.5#13.4#-1746443");
    }

    @Test
    void rejectsRowsWithTooFewColumns() {
        assertThatThrownBy(() -> new GdeltEventParser().parse("123\t20260705"))
                .isInstanceOf(GdeltParseException.class)
                .hasMessageContaining("expected at least 61");
    }

    @Test
    void rejectsInvalidNumbers() {
        String[] columns = eventColumns();
        columns[29] = "not-a-number";

        assertThatThrownBy(() -> new GdeltEventParser().parse(String.join("\t", columns)))
                .isInstanceOf(GdeltParseException.class)
                .hasMessageContaining("quad_class");
    }

    public static String eventRow() {
        return String.join("\t", eventColumns());
    }

    public static String mentionRow() {
        String[] columns = new String[15];
        columns[0] = "123";
        columns[1] = "20260705120000";
        columns[2] = "20260705121500";
        columns[3] = "1";
        columns[4] = "example.org";
        columns[5] = "https://example.org/a";
        columns[13] = "80";
        columns[14] = "-1.5";
        return joinNullable(columns);
    }

    public static String gkgRow() {
        String[] columns = new String[16];
        columns[0] = "20260705120000-1";
        columns[1] = "20260705120000";
        columns[2] = "1";
        columns[3] = "example.org";
        columns[4] = "https://example.org/a";
        columns[7] = "WB_133_INFORMATION_AND_COMMUNICATION_TECHNOLOGIES";
        columns[9] = "1#Berlin#GM#GM16#52.5#13.4#-1746443";
        columns[11] = "Jane Doe";
        columns[13] = "Example Org";
        columns[15] = "-1.5,2,3,4,5,6";
        return joinNullable(columns);
    }

    private static String[] eventColumns() {
        String[] columns = new String[61];
        columns[0] = "123";
        columns[1] = "20260705";
        columns[5] = "USA";
        columns[6] = "United States";
        columns[7] = "US";
        columns[15] = "RUS";
        columns[16] = "Russia";
        columns[17] = "RS";
        columns[26] = "042";
        columns[29] = "1";
        columns[30] = "-2.5";
        columns[34] = "-1.25";
        columns[60] = "https://example.org/a";
        return columns;
    }

    private static String joinNullable(String[] columns) {
        for (int index = 0; index < columns.length; index++) {
            if (columns[index] == null) {
                columns[index] = "";
            }
        }
        return String.join("\t", columns);
    }
}
