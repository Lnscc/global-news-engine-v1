package com.example.globalnewsenginev1.gdelt.staging.model;

import java.time.Instant;

public record GdeltStageGkg(
        String gkgRecordId,
        Instant documentDate,
        Integer sourceCollectionIdentifier,
        String sourceCommonName,
        String documentIdentifier,
        String themes,
        String persons,
        String organizations,
        String locations,
        String tone,
        String pageTitle
) {
}
