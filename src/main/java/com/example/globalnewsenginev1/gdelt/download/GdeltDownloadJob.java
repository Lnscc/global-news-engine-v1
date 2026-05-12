package com.example.globalnewsenginev1.gdelt.download;

import com.example.globalnewsenginev1.gdelt.GdeltSource;
import com.example.globalnewsenginev1.gdelt.model.GdeltFileType;
import com.example.globalnewsenginev1.ingestion.IngestionStatus;
import com.example.globalnewsenginev1.ingestion.RawFileDownloader;
import com.example.globalnewsenginev1.ingestion.RawFileStorage;
import com.example.globalnewsenginev1.ingestion.RawSourceFile;
import com.example.globalnewsenginev1.ingestion.SourceBatch;
import com.example.globalnewsenginev1.ingestion.SourceBatchRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GdeltDownloadJob {

    private static final Logger log = LoggerFactory.getLogger(GdeltDownloadJob.class);

    private final SourceBatchRepository batchRepository;
    private final RawFileDownloader downloader;
    private final RawFileStorage storage;

    public GdeltDownloadJob(
            SourceBatchRepository batchRepository,
            RawFileDownloader downloader,
            RawFileStorage storage
    ) {
        this.batchRepository = batchRepository;
        this.downloader = downloader;
        this.storage = storage;
    }

    @Transactional
    public boolean runNextBatch() {
        List<SourceBatch> candidateBatches = batchRepository
                .findTop10BySourceAndStatusInOrderByExternalBatchIdDesc(
                        GdeltSource.SOURCE,
                        List.of(IngestionStatus.DISCOVERED, IngestionStatus.FAILED)
                )
                .stream()
                .filter(this::hasExpectedFiles)
                .toList();

        if (candidateBatches.isEmpty()) {
            log.info("No discovered or failed GDELT batch is ready for download");
            return false;
        }

        for (SourceBatch batch : candidateBatches) {
            if (downloadBatch(batch)) {
                return true;
            }
        }

        return false;
    }

    private boolean downloadBatch(SourceBatch batch) {
        log.info("Starting GDELT download for batch {}", batch.getExternalBatchId());
        batch.markDownloading();

        boolean allDownloaded = true;
        for (GdeltFileType fileType : GdeltFileType.values()) {
            RawSourceFile file = batch.findFile(fileType.name()).orElseThrow();
            Path destination = storage.pathFor(batch, file);

            try {
                if (isReusableDownloadedFile(file, destination)) {
                    file.markDownloaded(destination.toString());
                    continue;
                }

                file.markDownloading();
                downloader.download(file.getUrl(), destination);
                file.markDownloaded(destination.toString());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                file.markFailed("Download interrupted");
                allDownloaded = false;
                break;
            } catch (Exception ex) {
                file.markFailed(ex.getMessage());
                allDownloaded = false;
                log.warn("Failed to download GDELT {} file for batch {}: {}", fileType, batch.getExternalBatchId(), ex.getMessage());
                log.debug("GDELT download failure detail", ex);
            }
        }

        if (allDownloaded && hasAllFilesDownloaded(batch)) {
            batch.markDownloaded();
            log.info("Downloaded GDELT batch {}", batch.getExternalBatchId());
        } else {
            batch.markFailed();
            log.warn("GDELT batch {} download did not complete", batch.getExternalBatchId());
        }

        batchRepository.save(batch);
        return allDownloaded;
    }

    private boolean hasExpectedFiles(SourceBatch batch) {
        return Arrays.stream(GdeltFileType.values())
                .allMatch(fileType -> batch.findFile(fileType.name()).isPresent());
    }

    private boolean hasAllFilesDownloaded(SourceBatch batch) {
        return Arrays.stream(GdeltFileType.values())
                .map(fileType -> batch.findFile(fileType.name()))
                .allMatch(file -> file.map(RawSourceFile::getStatus).filter(IngestionStatus.DOWNLOADED::equals).isPresent());
    }

    private boolean isReusableDownloadedFile(RawSourceFile file, Path destination) {
        return file.getStatus() == IngestionStatus.DOWNLOADED
                && file.getLocalPath() != null
                && Path.of(file.getLocalPath()).equals(destination)
                && Files.exists(destination);
    }
}
