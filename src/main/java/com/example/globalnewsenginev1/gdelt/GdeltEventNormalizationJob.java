package com.example.globalnewsenginev1.gdelt;

import com.example.globalnewsenginev1.ingestion.StagingRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class GdeltEventNormalizationJob {

    private static final Logger log = LoggerFactory.getLogger(GdeltEventNormalizationJob.class);

    private final GdeltEventRepository eventRepository;
    private final GdeltEventParser eventParser;
    private final int maxRowsPerRun;

    public GdeltEventNormalizationJob(
            GdeltEventRepository eventRepository,
            GdeltEventParser eventParser,
            @Value("${gdelt.normalize.events.max-rows-per-run:1000}") int maxRowsPerRun
    ) {
        this.eventRepository = eventRepository;
        this.eventParser = eventParser;
        this.maxRowsPerRun = maxRowsPerRun;
    }

    @Transactional
    public int run() {
        List<StagingRow> rows = eventRepository.findUnnormalizedRows(
                GdeltFileType.EVENTS.name(),
                PageRequest.of(0, maxRowsPerRun)
        );
        if (rows.isEmpty()) {
            log.info("No staged GDELT event rows are ready for normalization");
            return 0;
        }

        int normalized = 0;
        int invalid = 0;
        for (StagingRow row : rows) {
            GdeltEventRecord record = eventParser.parse(row.getRawLine()).orElse(null);
            if (record == null) {
                invalid++;
                continue;
            }
            if (eventRepository.findByGlobalEventId(record.globalEventId()).isPresent()) {
                continue;
            }

            eventRepository.save(new GdeltEvent(row, record));
            normalized++;
        }

        log.info("Normalized {} GDELT events from {} staged rows; {} rows were invalid", normalized, rows.size(), invalid);
        return normalized;
    }
}
