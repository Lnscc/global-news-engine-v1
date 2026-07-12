package com.example.globalnewsenginev1.articles.query;

import java.time.Instant;

public record ArticleSearchCriteria(
        String query,
        String domain,
        Instant firstSeenFrom,
        Instant firstSeenTo,
        String theme,
        String signalType,
        String direction
) {
    public static ArticleSearchCriteria defaults() {
        return new ArticleSearchCriteria(null, null, null, null, null, null, "desc");
    }
}
