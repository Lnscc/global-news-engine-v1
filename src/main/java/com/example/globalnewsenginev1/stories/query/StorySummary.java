package com.example.globalnewsenginev1.stories.query;

import java.time.Instant;
import java.util.UUID;

public record StorySummary(
        UUID id,
        String state,
        Instant effectiveFrom,
        Instant effectiveTo,
        StoryArticle representativeArticle,
        long memberCount
) {
}
