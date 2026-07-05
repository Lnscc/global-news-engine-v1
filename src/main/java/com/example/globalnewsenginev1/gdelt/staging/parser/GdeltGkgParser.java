package com.example.globalnewsenginev1.gdelt.staging.parser;

import com.example.globalnewsenginev1.gdelt.staging.model.GdeltStageGkg;

public class GdeltGkgParser {

    private static final int MINIMUM_COLUMNS = 16;

    public GdeltStageGkg parse(String rawTsv) {
        String[] columns = GdeltTsv.split(rawTsv, MINIMUM_COLUMNS, "GKG");
        return new GdeltStageGkg(
                GdeltTsv.requiredText(columns, 0, "gkg_record_id"),
                GdeltTsv.timestamp(columns, 1, "document_date"),
                GdeltTsv.integer(columns, 2, "source_collection_identifier"),
                GdeltTsv.text(columns, 3),
                GdeltTsv.text(columns, 4),
                GdeltTsv.text(columns, 7),
                GdeltTsv.text(columns, 11),
                GdeltTsv.text(columns, 13),
                GdeltTsv.text(columns, 9),
                GdeltTsv.text(columns, 15)
        );
    }
}
