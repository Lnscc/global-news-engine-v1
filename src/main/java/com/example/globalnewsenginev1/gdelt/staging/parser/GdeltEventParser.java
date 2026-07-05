package com.example.globalnewsenginev1.gdelt.staging.parser;

import com.example.globalnewsenginev1.gdelt.staging.model.GdeltStageEvent;

public class GdeltEventParser {

    private static final int MINIMUM_COLUMNS = 61;

    public GdeltStageEvent parse(String rawTsv) {
        String[] columns = GdeltTsv.split(rawTsv, MINIMUM_COLUMNS, "EVENTS");
        return new GdeltStageEvent(
                GdeltTsv.requiredLong(columns, 0, "global_event_id"),
                GdeltTsv.date(columns, 1, "event_date"),
                GdeltTsv.text(columns, 5),
                GdeltTsv.text(columns, 6),
                GdeltTsv.text(columns, 7),
                GdeltTsv.text(columns, 15),
                GdeltTsv.text(columns, 16),
                GdeltTsv.text(columns, 17),
                GdeltTsv.text(columns, 26),
                GdeltTsv.integer(columns, 29, "quad_class"),
                GdeltTsv.decimal(columns, 30, "goldstein_scale"),
                GdeltTsv.decimal(columns, 34, "avg_tone"),
                GdeltTsv.text(columns, 60)
        );
    }
}
