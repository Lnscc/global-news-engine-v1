package com.example.globalnewsenginev1.ingestion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class IngestionSchedulerTests {

    @Test
    void runsIngestionJobsOnStartup() {
        IngestionJob firstJob = mock(IngestionJob.class);
        IngestionJob secondJob = mock(IngestionJob.class);
        IngestionScheduler scheduler = new IngestionScheduler(List.of(firstJob, secondJob));

        scheduler.runOnStartup();

        verify(firstJob).runIngestion();
        verify(secondJob).runIngestion();
    }

    @Test
    void runsIngestionJobsOnSchedule() {
        IngestionJob job = mock(IngestionJob.class);
        IngestionScheduler scheduler = new IngestionScheduler(List.of(job));

        scheduler.runScheduled();

        verify(job, times(1)).runIngestion();
    }
}
