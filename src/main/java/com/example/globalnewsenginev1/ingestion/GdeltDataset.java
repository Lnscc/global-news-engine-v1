package com.example.globalnewsenginev1.ingestion;

import java.util.Arrays;
import java.util.Optional;

enum GdeltDataset {
    EVENTS("export.CSV.zip", "gdelt_raw_events"),
    MENTIONS("mentions.CSV.zip", "gdelt_raw_mentions"),
    GKG("gkg.csv.zip", "gdelt_raw_gkg");

    private final String fileSuffix;
    private final String tableName;

    GdeltDataset(String fileSuffix, String tableName) {
        this.fileSuffix = fileSuffix;
        this.tableName = tableName;
    }

    String fileSuffix() {
        return fileSuffix;
    }

    String tableName() {
        return tableName;
    }

    static Optional<GdeltDataset> fromFileSuffix(String fileSuffix) {
        return Arrays.stream(values())
                .filter(dataset -> dataset.fileSuffix.equals(fileSuffix))
                .findFirst();
    }
}
