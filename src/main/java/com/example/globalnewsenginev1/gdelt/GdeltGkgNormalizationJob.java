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
public class GdeltGkgNormalizationJob {

    private static final Logger log = LoggerFactory.getLogger(GdeltGkgNormalizationJob.class);

    private final GdeltGkgRepository gkgRepository;
    private final GdeltGkgParser gkgParser;
    private final int maxRowsPerRun;

    public GdeltGkgNormalizationJob(
            GdeltGkgRepository gkgRepository,
            GdeltGkgParser gkgParser,
            @Value("${gdelt.normalize.gkg.max-rows-per-run:1000}") int maxRowsPerRun
    ) {
        this.gkgRepository = gkgRepository;
        this.gkgParser = gkgParser;
        this.maxRowsPerRun = maxRowsPerRun;
    }

    @Transactional
    public int run() {
        List<StagingRow> rows = gkgRepository.findUnnormalizedRows(
                GdeltFileType.GKG.name(),
                PageRequest.of(0, maxRowsPerRun)
        );
        if (rows.isEmpty()) {
            log.info("No staged GDELT GKG rows are ready for normalization");
            return 0;
        }

        int normalized = 0;
        int invalid = 0;
        for (StagingRow row : rows) {
            GdeltGkgRecord record = gkgParser.parse(row.getRawLine()).orElse(null);
            if (record == null) {
                invalid++;
                continue;
            }
            if (gkgRepository.findByGkgRecordId(record.gkgRecordId()).isPresent()) {
                continue;
            }

            gkgRepository.save(new GdeltGkg(row, record));
            normalized++;
        }

        log.info("Normalized {} GDELT GKG records from {} staged rows; {} rows were invalid", normalized, rows.size(), invalid);
        return normalized;
    }
}
