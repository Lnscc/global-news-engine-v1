package com.example.globalnewsenginev1.gdelt;

import com.example.globalnewsenginev1.ingestion.SourceBatch;
import com.example.globalnewsenginev1.ingestion.SourceBatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class GdeltDiscoveryJob {

    static final String SOURCE = "GDELT";

    private static final Logger log = LoggerFactory.getLogger(GdeltDiscoveryJob.class);

    private final GdeltManifestClient manifestClient;
    private final GdeltManifestParser manifestParser;
    private final SourceBatchRepository batchRepository;
    private final int maxBatchesPerRun;

    public GdeltDiscoveryJob(
            GdeltManifestClient manifestClient,
            GdeltManifestParser manifestParser,
            SourceBatchRepository batchRepository,
            @Value("${gdelt.ingestion.max-batches-per-run:1}") int maxBatchesPerRun
    ) {
        this.manifestClient = manifestClient;
        this.manifestParser = manifestParser;
        this.batchRepository = batchRepository;
        this.maxBatchesPerRun = maxBatchesPerRun;
    }

    @Transactional
    public int run() throws IOException, InterruptedException {
        log.info("Starting GDELT discovery");
        String manifestBody = manifestClient.fetchMasterFileList();
        List<GdeltManifestEntry> entries = manifestParser.parse(manifestBody);

        Map<String, List<GdeltManifestEntry>> entriesByTimestamp = entries.stream()
                .collect(Collectors.groupingBy(GdeltManifestEntry::batchTimestamp));

        List<Map.Entry<String, List<GdeltManifestEntry>>> newestBatchEntries = entriesByTimestamp.entrySet().stream()
                .sorted(Map.Entry.<String, List<GdeltManifestEntry>>comparingByKey().reversed())
                .limit(maxBatchesPerRun)
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
}
