package com.example.globalnewsenginev1.gdelt;

import com.example.globalnewsenginev1.ingestion.SourceBatch;
import com.example.globalnewsenginev1.ingestion.SourceBatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class GdeltDiscoveryJob {

    static final String SOURCE = "GDELT";

    private static final Logger log = LoggerFactory.getLogger(GdeltDiscoveryJob.class);
    private static final DateTimeFormatter BATCH_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final GdeltManifestClient manifestClient;
    private final GdeltManifestParser manifestParser;
    private final SourceBatchRepository batchRepository;
    private final Duration minimumBatchAge;
    private final Clock clock;

    @Autowired
    public GdeltDiscoveryJob(
            GdeltManifestClient manifestClient,
            GdeltManifestParser manifestParser,
            SourceBatchRepository batchRepository,
            @Value("${gdelt.discovery.minimum-batch-age:PT10M}") Duration minimumBatchAge
    ) {
        this(manifestClient, manifestParser, batchRepository, minimumBatchAge, Clock.systemUTC());
    }

    GdeltDiscoveryJob(
            GdeltManifestClient manifestClient,
            GdeltManifestParser manifestParser,
            SourceBatchRepository batchRepository,
            Duration minimumBatchAge,
            Clock clock
    ) {
        this.manifestClient = manifestClient;
        this.manifestParser = manifestParser;
        this.batchRepository = batchRepository;
        this.minimumBatchAge = minimumBatchAge;
        this.clock = clock;
    }

    @Transactional
    public int run() throws IOException, InterruptedException {
        log.info("Starting GDELT discovery");
        String manifestBody = manifestClient.fetchMasterFileList();
        List<GdeltManifestEntry> entries = manifestParser.parse(manifestBody);

        Map<String, List<GdeltManifestEntry>> entriesByTimestamp = entries.stream()
                .collect(Collectors.groupingBy(GdeltManifestEntry::batchTimestamp));

        Instant newestAllowedBatchTime = Instant.now(clock).minus(minimumBatchAge);
        List<Map.Entry<String, List<GdeltManifestEntry>>> newestBatchEntries = entriesByTimestamp.entrySet().stream()
                .filter(entry -> isOldEnough(entry.getKey(), newestAllowedBatchTime))
                .sorted(Map.Entry.<String, List<GdeltManifestEntry>>comparingByKey().reversed())
                .toList();

        int savedCount = 0;
        for (Map.Entry<String, List<GdeltManifestEntry>> batchEntries : newestBatchEntries) {
            SourceBatch batch = batchRepository.findBySourceAndExternalBatchId(SOURCE, batchEntries.getKey())
                    .orElseGet(() -> new SourceBatch(SOURCE, batchEntries.getKey()));

            batchEntries.getValue().stream()
                    .sorted(Comparator.comparing(GdeltManifestEntry::fileType))
                    .forEach(entry -> batch.putFile(
                            entry.fileType().name(),
                            entry.url(),
                            entry.sizeBytes(),
                            entry.fileHash()
                    ));

            batchRepository.save(batch);
            savedCount++;
        }

        log.info("Discovered {} newest GDELT source batches from {} manifest entries", savedCount, entries.size());
        return savedCount;
    }

    private boolean isOldEnough(String batchTimestamp, Instant newestAllowedBatchTime) {
        return parseBatchTime(batchTimestamp)
                .map(batchTime -> !batchTime.isAfter(newestAllowedBatchTime))
                .orElse(false);
    }

    private Optional<Instant> parseBatchTime(String batchTimestamp) {
        try {
            return Optional.of(LocalDateTime.parse(batchTimestamp, BATCH_TIMESTAMP_FORMAT).toInstant(ZoneOffset.UTC));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }
}
