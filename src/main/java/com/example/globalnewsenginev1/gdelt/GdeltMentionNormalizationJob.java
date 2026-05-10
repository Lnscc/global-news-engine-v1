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
public class GdeltMentionNormalizationJob {

    private static final Logger log = LoggerFactory.getLogger(GdeltMentionNormalizationJob.class);

    private final GdeltMentionRepository mentionRepository;
    private final GdeltMentionParser mentionParser;
    private final int maxRowsPerRun;

    public GdeltMentionNormalizationJob(
            GdeltMentionRepository mentionRepository,
            GdeltMentionParser mentionParser,
            @Value("${gdelt.normalize.mentions.max-rows-per-run:1000}") int maxRowsPerRun
    ) {
        this.mentionRepository = mentionRepository;
        this.mentionParser = mentionParser;
        this.maxRowsPerRun = maxRowsPerRun;
    }

    @Transactional
    public int run() {
        List<StagingRow> rows = mentionRepository.findUnnormalizedRows(
                GdeltFileType.MENTIONS.name(),
                PageRequest.of(0, maxRowsPerRun)
        );
        if (rows.isEmpty()) {
            log.info("No staged GDELT mention rows are ready for normalization");
            return 0;
        }

        int normalized = 0;
        int invalid = 0;
        for (StagingRow row : rows) {
            if (mentionRepository.existsByStagingRow(row)) {
                row.markNormalized();
                normalized++;
                continue;
            }

            GdeltMentionRecord record = mentionParser.parse(row.getRawLine()).orElse(null);
            if (record == null) {
                row.markNormalizationSkipped("Invalid GDELT mention row");
                invalid++;
                continue;
            }

            mentionRepository.save(new GdeltMention(row, record));
            row.markNormalized();
            normalized++;
        }

        log.info("Normalized {} GDELT mentions from {} staged rows; {} rows were invalid", normalized, rows.size(), invalid);
        return normalized;
    }
}
