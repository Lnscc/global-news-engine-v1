package com.example.globalnewsenginev1.stories.query;

import java.time.Instant;

public record StoryArticle(
        long id,
        String canonicalUrl,
        String domain,
        Instant firstSeenAt,
        String title,
        Instant publishedAt,
        String mainImageUrl
) {
}
