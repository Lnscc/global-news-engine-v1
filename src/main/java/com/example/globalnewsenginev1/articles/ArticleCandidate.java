package com.example.globalnewsenginev1.articles;

import com.example.globalnewsenginev1.ingestion.SourceBatch;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ArticleCandidate(
        Long sourceRecordId,
        SourceBatch sourceBatch,
        LocalDateTime publishedAt,
        String sourceName,
        String documentIdentifier,
        String themes,
        String persons,
        String organizations,
        BigDecimal tone,
        BigDecimal positiveScore,
        BigDecimal negativeScore,
        BigDecimal polarity,
        BigDecimal wordCount,
        String sharingImage,
        String relatedImages,
        String extras
) {
}
