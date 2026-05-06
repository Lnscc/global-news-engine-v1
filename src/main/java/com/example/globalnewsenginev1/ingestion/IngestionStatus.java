package com.example.globalnewsenginev1.ingestion;

public enum IngestionStatus {
    DISCOVERED,
    DOWNLOADING,
    DOWNLOADED,
    PARSING,
    PARSED,
    FAILED
}
