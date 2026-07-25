package com.example.globalnewsenginev1.stories.snapshot;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
class StorySnapshotMetrics {

    private final Counter snapshotsCreated;
    private final Counter snapshotsReused;
    private final Counter members;
    private final Counter candidates;
    private final Counter sameStory;
    private final Counter uncertain;
    private final Counter failures;
    private final Timer latency;

    StorySnapshotMetrics(MeterRegistry registry) {
        snapshotsCreated = registry.counter("stories.snapshot.created");
        snapshotsReused = registry.counter("stories.snapshot.reused");
        members = registry.counter("stories.snapshot.members");
        candidates = registry.counter("stories.snapshot.candidates");
        sameStory = registry.counter("stories.snapshot.pairs", "result", "SAME_STORY");
        uncertain = registry.counter("stories.snapshot.pairs", "result", "UNCERTAIN");
        failures = registry.counter("stories.snapshot.failures");
        latency = registry.timer("stories.snapshot.processing.latency");
    }

    void snapshot(boolean created, int memberCount) {
        if (created) {
            snapshotsCreated.increment();
            members.increment(memberCount);
        } else {
            snapshotsReused.increment();
        }
    }

    void results(long candidateCount, long sameStoryCount, long uncertainCount) {
        candidates.increment(candidateCount);
        sameStory.increment(sameStoryCount);
        uncertain.increment(uncertainCount);
    }

    void failure() {
        failures.increment();
    }

    void latency(long nanos) {
        latency.record(nanos, TimeUnit.NANOSECONDS);
    }
}
