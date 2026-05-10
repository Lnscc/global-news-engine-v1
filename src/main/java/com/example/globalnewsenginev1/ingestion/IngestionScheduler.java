package com.example.globalnewsenginev1.ingestion;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IngestionScheduler {

    private final List<IngestionJob> jobs;

    public IngestionScheduler(List<IngestionJob> jobs) {
        this.jobs = jobs;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runOnStartup() {
        runIngestion();
    }

    @Scheduled(
            initialDelayString = "${ingestion.fixed-delay:PT15M}",
            fixedDelayString = "${ingestion.fixed-delay:PT15M}"
    )
    public void runScheduled() {
        runIngestion();
    }

    private void runIngestion() {
        jobs.forEach(IngestionJob::runIngestion);
    }
}
