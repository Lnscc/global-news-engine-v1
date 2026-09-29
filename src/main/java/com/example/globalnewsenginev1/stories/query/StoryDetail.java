package com.example.globalnewsenginev1.stories.query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StoryDetail(
        UUID id,
        String state,
        Instant effectiveFrom,
        Instant effectiveTo,
        StoryArticle representativeArticle,
        List<StoryMember> members
) {
    public StoryDetail {
        members = List.copyOf(members);
    }
}
