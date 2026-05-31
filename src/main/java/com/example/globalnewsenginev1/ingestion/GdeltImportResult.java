package com.example.globalnewsenginev1.ingestion;

public record GdeltImportResult(String sourceFile, long rowCount, boolean skipped) {
}
