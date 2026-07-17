package com.example.globalnewsenginev1.gdelt.retention;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.example.globalnewsenginev1.gdelt.retention.GdeltPayloadRetentionRepository.RetentionDataset.EVENTS;
import static com.example.globalnewsenginev1.gdelt.retention.GdeltPayloadRetentionRepository.RetentionDataset.GKG;
import static com.example.globalnewsenginev1.gdelt.retention.GdeltPayloadRetentionRepository.RetentionDataset.MENTIONS;

@Service
public class GdeltPayloadRetentionService {

    private final GdeltPayloadRetentionRepository repository;
    private final TransactionTemplate transactionTemplate;

    GdeltPayloadRetentionService(
            GdeltPayloadRetentionRepository repository,
            TransactionTemplate transactionTemplate
    ) {
        this.repository = repository;
        this.transactionTemplate = transactionTemplate;
    }

    public GdeltPayloadRetentionResult cleanupBatch(Instant cutoff, int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than zero");
        }
        return new GdeltPayloadRetentionResult(
                deleteInTransaction(EVENTS, cutoff, batchSize),
                deleteInTransaction(MENTIONS, cutoff, batchSize),
                deleteInTransaction(GKG, cutoff, batchSize));
    }

    public List<GdeltPayloadRetentionHealth> health(Instant now, Duration retentionPeriod) {
        validateRetentionPeriod(retentionPeriod);
        return repository.health(now.minus(retentionPeriod));
    }

    static void validateRetentionPeriod(Duration retentionPeriod) {
        if (retentionPeriod.isNegative()) {
            throw new IllegalArgumentException("retentionPeriod must not be negative");
        }
    }

    private long deleteInTransaction(
            GdeltPayloadRetentionRepository.RetentionDataset dataset,
            Instant cutoff,
            int batchSize
    ) {
        Long deleted = transactionTemplate.execute(status -> repository.deleteEligible(dataset, cutoff, batchSize));
        return deleted == null ? 0 : deleted;
    }
}
