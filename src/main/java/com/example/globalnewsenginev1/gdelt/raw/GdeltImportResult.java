package com.example.globalnewsenginev1.gdelt.raw;

public record GdeltImportResult(String sourceFile, long rowCount, boolean skipped) {
}
