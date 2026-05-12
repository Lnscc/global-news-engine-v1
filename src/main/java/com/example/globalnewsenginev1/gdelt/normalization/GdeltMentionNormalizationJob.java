package com.example.globalnewsenginev1.gdelt.normalization;

import com.example.globalnewsenginev1.gdelt.model.GdeltFileType;
import com.example.globalnewsenginev1.gdelt.model.GdeltMention;
import com.example.globalnewsenginev1.gdelt.parser.GdeltMentionParser;
import com.example.globalnewsenginev1.gdelt.parser.GdeltMentionRecord;
import com.example.globalnewsenginev1.gdelt.repository.GdeltMentionRepository;
import com.example.globalnewsenginev1.ingestion.StagingRow;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GdeltMentionNormalizationJob {

    private static final Logger log = LoggerFactory.getLogger(GdeltMentionNormalizationJob.class);

    private final GdeltMentionRepository mentionRepository;
    private final GdeltMentionParser mentionParser;

    public GdeltMentionNormalizationJob(
            GdeltMentionRepository mentionRepository,
            GdeltMentionParser mentionParser
    ) {
        this.mentionRepository = mentionRepository;
        this.mentionParser = mentionParser;
    }

    @Transactional
    public int run() {
        List<StagingRow> rows = mentionRepository.findUnnormalizedRows(GdeltFileType.MENTIONS.name());
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
