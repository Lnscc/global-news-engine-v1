package com.example.globalnewsenginev1.gdelt.retention;

public record GdeltPayloadRetentionHealth(
        String datasetType,
        long eligiblePayloadRows,
        long retainedPayloadRows
) {
}
