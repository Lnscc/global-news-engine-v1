package com.example.globalnewsenginev1.gdelt.staging.model;

import java.time.Instant;

public record GdeltStageMention(
        Long globalEventId,
        Instant eventTimeDate,
        Instant mentionTimeDate,
        Integer mentionType,
        String mentionSourceName,
        String mentionIdentifier,
        Integer confidence,
        Double mentionDocTone
) {
}
