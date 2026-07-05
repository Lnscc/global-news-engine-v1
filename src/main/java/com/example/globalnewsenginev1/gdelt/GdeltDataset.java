package com.example.globalnewsenginev1.gdelt;

import java.util.Arrays;
import java.util.Optional;

public enum GdeltDataset {
    EVENTS("export.CSV.zip", "gdelt_raw_events"),
    MENTIONS("mentions.CSV.zip", "gdelt_raw_mentions"),
    GKG("gkg.csv.zip", "gdelt_raw_gkg");

    private final String fileSuffix;
    private final String tableName;

    GdeltDataset(String fileSuffix, String tableName) {
        this.fileSuffix = fileSuffix;
        this.tableName = tableName;
    }

    public String fileSuffix() {
        return fileSuffix;
    }

    public String tableName() {
        return tableName;
    }

    public static Optional<GdeltDataset> fromFileSuffix(String fileSuffix) {
        return Arrays.stream(values())
                .filter(dataset -> dataset.fileSuffix.equals(fileSuffix))
                .findFirst();
    }
}
