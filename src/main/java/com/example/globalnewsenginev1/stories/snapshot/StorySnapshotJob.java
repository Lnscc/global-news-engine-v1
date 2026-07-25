package com.example.globalnewsenginev1.stories.snapshot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "stories.snapshots.incremental.enabled",
        havingValue = "true", matchIfMissing = true)
class StorySnapshotJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(StorySnapshotJob.class);

    private final StorySnapshotService service;
    private final int maxVersionsPerRun;

    StorySnapshotJob(
            StorySnapshotService service,
            @Value("${stories.snapshots.max-versions-per-run:3}") int maxVersionsPerRun
    ) {
        this.service = service;
        this.maxVersionsPerRun = maxVersionsPerRun;
    }

    @Scheduled(
            initialDelayString = "${stories.snapshots.incremental.initial-delay:PT1M}",
            fixedDelayString = "${stories.snapshots.incremental.poll-interval:PT5M}"
    )
    void processIncremental() {
        StorySnapshotService.ProcessingResult result =
                service.processIncremental(maxVersionsPerRun);
        if (result.selectedVersions() > 0) {
            LOGGER.info("Story snapshot run completed: selected={}, succeeded={}, reused={}, failed={}",
                    result.selectedVersions(), result.succeededVersions(),
                    result.reusedVersions(), result.failedVersions());
        }
    }
}
