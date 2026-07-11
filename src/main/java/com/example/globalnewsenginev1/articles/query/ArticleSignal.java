package com.example.globalnewsenginev1.articles.query;

import java.time.Instant;

public record ArticleSignal(
        long id,
        String signalType,
        long sourceId,
        Instant sourceTimestamp,
        Long globalEventId,
        String eventCode,
        String themes,
        String persons,
        String organizations,
        String locations,
        Double toneValue,
        String toneRaw
) {
}
