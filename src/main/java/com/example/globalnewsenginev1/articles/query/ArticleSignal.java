package com.example.globalnewsenginev1.articles.query;

import com.example.globalnewsenginev1.articles.normalization.GkgLocation;

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
        List<String> persons,
        List<String> organizations,
        List<GkgLocation> locations,
        Double toneValue,
        Double tonePositiveScore,
        Double toneNegativeScore,
        Double tonePolarity,
        Double toneActivityReferenceDensity,
        Double toneSelfGroupReferenceDensity,
        Integer toneWordCount
) {
}
