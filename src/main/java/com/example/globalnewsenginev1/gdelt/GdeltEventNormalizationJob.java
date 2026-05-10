package com.example.globalnewsenginev1.gdelt;

import com.example.globalnewsenginev1.ingestion.StagingRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class GdeltEventNormalizationJob {

    private static final Logger log = LoggerFactory.getLogger(GdeltEventNormalizationJob.class);

    private final GdeltEventRepository eventRepository;
    private final GdeltEventParser eventParser;

    public GdeltEventNormalizationJob(
            GdeltEventRepository eventRepository,
            GdeltEventParser eventParser
    ) {
        this.eventRepository = eventRepository;
        this.eventParser = eventParser;
    }

    @Transactional
    public int run() {
        List<StagingRow> rows = eventRepository.findUnnormalizedRows(GdeltFileType.EVENTS.name());
        if (rows.isEmpty()) {
            log.info("No staged GDELT event rows are ready for normalization");
            return 0;
        }

        int normalized = 0;
        int invalid = 0;
        for (StagingRow row : rows) {
            if (eventRepository.existsByStagingRow(row)) {
                row.markNormalized();
                normalized++;
                continue;
            }

            GdeltEventRecord record = eventParser.parse(row.getRawLine()).orElse(null);
            if (record == null) {
                row.markNormalizationSkipped("Invalid GDELT event row");
                invalid++;
                continue;
            }
            if (eventRepository.findByGlobalEventId(record.globalEventId()).isPresent()) {
                row.markNormalizationSkipped("Duplicate GDELT event id " + record.globalEventId());
                continue;
            }

            eventRepository.save(new GdeltEvent(row, record));
            row.markNormalized();
            normalized++;
        }

        log.info("Normalized {} GDELT events from {} staged rows; {} rows were invalid", normalized, rows.size(), invalid);
        return normalized;
    }
}
