package com.example.globalnewsenginev1.gdelt;

import java.util.Arrays;
import java.util.Optional;

public enum GdeltDataset {
    EVENTS("export.CSV.zip", "gdelt_event_payloads"),
    MENTIONS("mentions.CSV.zip", "gdelt_mention_payloads"),
    GKG("gkg.csv.zip", "gdelt_gkg_payloads");

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
