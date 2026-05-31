package com.example.globalnewsenginev1.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@ConditionalOnProperty(name = "gdelt.import.timestamp")
class GdeltSingleWindowImportRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(GdeltSingleWindowImportRunner.class);

    private final GdeltRawImporter importer;
    private final Instant sourceTimestamp;

    GdeltSingleWindowImportRunner(
            GdeltRawImporter importer,
            @Value("${gdelt.import.timestamp}") Instant sourceTimestamp
    ) {
        this.importer = importer;
        this.sourceTimestamp = sourceTimestamp;
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        importer.importWindow(sourceTimestamp).forEach(result ->
                LOGGER.info("Imported {} rows from {} (skipped={})",
                        result.rowCount(), result.sourceFile(), result.skipped()));
    }
}
