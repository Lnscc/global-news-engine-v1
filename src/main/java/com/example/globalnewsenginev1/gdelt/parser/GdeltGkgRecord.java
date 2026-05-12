package com.example.globalnewsenginev1.gdelt.parser;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GdeltGkgRecord(
        String gkgRecordId,
        LocalDateTime date,
        Integer sourceCollectionIdentifier,
        String sourceCommonName,
        String documentIdentifier,
        String counts,
        String v2Counts,
        String themes,
        String v2Themes,
        String locations,
        String v2Locations,
        String persons,
        String v2Persons,
        String organizations,
        String v2Organizations,
        BigDecimal tone,
        BigDecimal positiveScore,
        BigDecimal negativeScore,
        BigDecimal polarity,
        BigDecimal activityDensity,
        BigDecimal selfGroupReferenceDensity,
        BigDecimal wordCount,
        String dates,
        String gcam,
        String sharingImage,
        String relatedImages,
        String socialImageEmbeds,
        String socialVideoEmbeds,
        String quotations,
        String allNames,
        String amounts,
        String translationInfo,
        String extras
) {
}
