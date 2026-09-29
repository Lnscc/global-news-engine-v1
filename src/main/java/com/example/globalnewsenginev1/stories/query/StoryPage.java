package com.example.globalnewsenginev1.stories.query;

import java.util.List;

public record StoryPage(List<StorySummary> stories, int offset, int limit, long total) {
    public StoryPage {
        stories = List.copyOf(stories);
    }
}
