package com.example.globalnewsenginev1.gdelt;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record GdeltEventRecord(
        long globalEventId,
        LocalDate eventDate,
        int eventYear,
        String actor1Code,
        String actor1Name,
        String actor1CountryCode,
        String actor2Code,
        String actor2Name,
        String actor2CountryCode,
        boolean rootEvent,
        String eventCode,
        String eventBaseCode,
        String eventRootCode,
        Integer quadClass,
        BigDecimal goldsteinScale,
        Integer numMentions,
        Integer numSources,
        Integer numArticles,
        BigDecimal averageTone,
        String actionGeoFullName,
        String actionGeoCountryCode,
        BigDecimal actionGeoLatitude,
        BigDecimal actionGeoLongitude,
        LocalDateTime dateAdded,
        String sourceUrl
) {
}
