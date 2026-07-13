package com.example.globalnewsenginev1.articles.query;

import java.time.Instant;
import java.util.List;

public record ArticleSignal(
        long id,
        String signalType,
        long sourceId,
        Instant sourceTimestamp,
        Long globalEventId,
        String eventCode,
        List<String> themes,
        String persons,
        String organizations,
        String locations,
        Double toneValue,
        String toneRaw
) {
}
