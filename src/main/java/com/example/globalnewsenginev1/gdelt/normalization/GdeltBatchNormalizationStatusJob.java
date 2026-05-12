package com.example.globalnewsenginev1.gdelt.normalization;

import com.example.globalnewsenginev1.gdelt.GdeltSource;
import com.example.globalnewsenginev1.gdelt.repository.GdeltEventRepository;
import com.example.globalnewsenginev1.gdelt.repository.GdeltGkgRepository;
import com.example.globalnewsenginev1.gdelt.repository.GdeltMentionRepository;
import com.example.globalnewsenginev1.gdelt.model.GdeltFileType;
import com.example.globalnewsenginev1.ingestion.IngestionStatus;
import com.example.globalnewsenginev1.ingestion.SourceBatch;
import com.example.globalnewsenginev1.ingestion.SourceBatchRepository;
import com.example.globalnewsenginev1.ingestion.StagingRowRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GdeltBatchNormalizationStatusJob {

    private static final Logger log = LoggerFactory.getLogger(GdeltBatchNormalizationStatusJob.class);

    private final SourceBatchRepository batchRepository;
    private final StagingRowRepository stagingRowRepository;
    private final GdeltEventRepository eventRepository;
    private final GdeltMentionRepository mentionRepository;
    private final GdeltGkgRepository gkgRepository;

    public GdeltBatchNormalizationStatusJob(
            SourceBatchRepository batchRepository,
            StagingRowRepository stagingRowRepository,
            GdeltEventRepository eventRepository,
            GdeltMentionRepository mentionRepository,
            GdeltGkgRepository gkgRepository
    ) {
        this.batchRepository = batchRepository;
        this.stagingRowRepository = stagingRowRepository;
        this.eventRepository = eventRepository;
        this.mentionRepository = mentionRepository;
        this.gkgRepository = gkgRepository;
    }

    @Transactional
    public int run() {
        List<SourceBatch> batches = batchRepository.findTop10BySourceAndStatusInOrderByExternalBatchIdDesc(
                GdeltSource.SOURCE,
                List.of(IngestionStatus.PARSED, IngestionStatus.NORMALIZING)
        );

        int updated = 0;
        for (SourceBatch batch : batches) {
            IngestionStatus previousStatus = batch.getStatus();
            if (isFullyNormalized(batch)) {
                batch.markNormalized();
            } else {
                batch.markNormalizing();
            }

            if (batch.getStatus() != previousStatus) {
                batchRepository.save(batch);
                updated++;
                log.info("Updated GDELT batch {} status from {} to {}", batch.getExternalBatchId(), previousStatus, batch.getStatus());
            }
        }

        return updated;
    }

    private boolean isFullyNormalized(SourceBatch batch) {
        return stagingRowRepository.countBySourceBatch(batch) > 0
                && unhandledCount(batch, GdeltFileType.EVENTS) == 0
                && unhandledCount(batch, GdeltFileType.MENTIONS) == 0
                && unhandledCount(batch, GdeltFileType.GKG) == 0;
    }

    private long unhandledCount(SourceBatch batch, GdeltFileType fileType) {
        return stagingRowRepository.countUnhandledBySourceBatchAndFileType(batch, fileType.name());
    }
}
