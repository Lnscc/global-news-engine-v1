package com.example.globalnewsenginev1.gdelt;

import com.example.globalnewsenginev1.ingestion.IngestionStatus;
import com.example.globalnewsenginev1.ingestion.RawSourceFile;
import com.example.globalnewsenginev1.ingestion.RawZipLineReader;
import com.example.globalnewsenginev1.ingestion.SourceBatch;
import com.example.globalnewsenginev1.ingestion.SourceBatchRepository;
import com.example.globalnewsenginev1.ingestion.StagingRow;
import com.example.globalnewsenginev1.ingestion.StagingRowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

@Component
public class GdeltParseJob {

    private static final Logger log = LoggerFactory.getLogger(GdeltParseJob.class);

    private final SourceBatchRepository batchRepository;
    private final StagingRowRepository stagingRowRepository;
    private final RawZipLineReader zipLineReader;
    private final int maxRowsPerFile;

    public GdeltParseJob(
            SourceBatchRepository batchRepository,
            StagingRowRepository stagingRowRepository,
            RawZipLineReader zipLineReader,
            @Value("${gdelt.parse.max-rows-per-file:1000}") int maxRowsPerFile
    ) {
        this.batchRepository = batchRepository;
        this.stagingRowRepository = stagingRowRepository;
        this.zipLineReader = zipLineReader;
        this.maxRowsPerFile = maxRowsPerFile;
    }

    @Transactional
    public boolean runNextBatch() {
        Optional<SourceBatch> nextBatch = batchRepository
                .findTop10BySourceAndStatusOrderByExternalBatchIdDesc(GdeltDiscoveryJob.SOURCE, IngestionStatus.DOWNLOADED)
                .stream()
                .filter(this::hasDownloadedExpectedFiles)
                .findFirst();

        if (nextBatch.isEmpty()) {
            log.info("No downloaded GDELT batch is ready for parsing");
            return false;
        }

        SourceBatch batch = nextBatch.get();
        log.info("Starting GDELT parse for batch {}", batch.getExternalBatchId());
        batch.markParsing();

        try {
            int totalRows = 0;
            for (GdeltFileType fileType : GdeltFileType.values()) {
                RawSourceFile file = batch.findFile(fileType.name()).orElseThrow();
                Path path = Path.of(file.getLocalPath());

                int rows = zipLineReader.readLines(path, maxRowsPerFile, (lineNumber, rawLine) -> {
                    if (!stagingRowRepository.existsByRawSourceFileAndLineNumber(file, lineNumber)) {
                        stagingRowRepository.save(new StagingRow(batch, file, lineNumber, rawLine));
                    }
                });
                totalRows += rows;
                log.info("Parsed {} rows from GDELT {} file for batch {}", rows, fileType, batch.getExternalBatchId());
            }

            batch.markParsed();
            batchRepository.save(batch);
            log.info("Parsed GDELT batch {} into {} staging rows", batch.getExternalBatchId(), totalRows);
            return true;
        } catch (Exception ex) {
            batch.markFailed();
            batchRepository.save(batch);
            log.warn("Failed to parse GDELT batch {}: {}", batch.getExternalBatchId(), ex.getMessage());
            log.debug("GDELT parse failure detail", ex);
            return false;
        }
    }

    private boolean hasDownloadedExpectedFiles(SourceBatch batch) {
        return Arrays.stream(GdeltFileType.values())
                .map(fileType -> batch.findFile(fileType.name()))
                .allMatch(file -> file
                        .filter(rawFile -> rawFile.getStatus() == IngestionStatus.DOWNLOADED)
                        .filter(rawFile -> rawFile.getLocalPath() != null && !rawFile.getLocalPath().isBlank())
                        .isPresent());
    }
}
