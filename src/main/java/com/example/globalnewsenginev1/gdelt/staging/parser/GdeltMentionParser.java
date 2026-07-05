package com.example.globalnewsenginev1.gdelt.staging.parser;

import com.example.globalnewsenginev1.gdelt.staging.model.GdeltStageMention;

public class GdeltMentionParser {

    private static final int MINIMUM_COLUMNS = 16;

    public GdeltStageMention parse(String rawTsv) {
        String[] columns = GdeltTsv.split(rawTsv, MINIMUM_COLUMNS, "MENTIONS");
        return new GdeltStageMention(
                GdeltTsv.requiredLong(columns, 0, "global_event_id"),
                GdeltTsv.timestamp(columns, 1, "event_time_date"),
                GdeltTsv.timestamp(columns, 2, "mention_time_date"),
                GdeltTsv.integer(columns, 3, "mention_type"),
                GdeltTsv.text(columns, 4),
                GdeltTsv.text(columns, 5),
                GdeltTsv.integer(columns, 11, "confidence"),
                GdeltTsv.decimal(columns, 13, "mention_doc_tone")
        );
    }
}
