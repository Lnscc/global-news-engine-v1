package com.example.globalnewsenginev1.stories.snapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared anchor/overlap nomination and merge winner rules for publishing and promotion. */
final class StoryIdentity {
    private StoryIdentity() { }

    static Match match(List<StoryPublisher.Story> stories, List<StoryPublisher.Membership> members,
                       Map<String, Integer> componentByArticle) {
        Map<Integer, List<StoryPublisher.Story>> nominees = new HashMap<>();
        int splits = 0;
        for (var story : stories) {
            Map<Integer, Integer> overlap = new HashMap<>();
            for (var member : members) {
                Integer component = componentByArticle.get(member.ref());
                if (member.storyId().equals(story.id()) && component != null) {
                    overlap.merge(component, 1, Integer::sum);
                }
            }
            if (overlap.size() > 1) splits++;
            Integer nomination = componentByArticle.get(story.anchor());
            if (nomination == null || !overlap.containsKey(nomination)) {
                nomination = overlap.entrySet().stream()
                        .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed()
                                .thenComparing(Map.Entry.comparingByKey()))
                        .map(Map.Entry::getKey).findFirst().orElse(null);
            }
            if (nomination != null) nominees.computeIfAbsent(nomination, ignored -> new ArrayList<>()).add(story);
        }
        return new Match(nominees, splits);
    }

    static StoryPublisher.Story winner(List<StoryPublisher.Story> candidates) {
        return candidates.stream().min(Comparator.comparing(StoryPublisher.Story::createdAt)
                .thenComparing(story -> story.publicId().toString())).orElse(null);
    }

    record Match(Map<Integer, List<StoryPublisher.Story>> nominees, int splits) { }
}
