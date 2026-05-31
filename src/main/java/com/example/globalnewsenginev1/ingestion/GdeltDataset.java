package com.example.globalnewsenginev1.ingestion;

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
}
