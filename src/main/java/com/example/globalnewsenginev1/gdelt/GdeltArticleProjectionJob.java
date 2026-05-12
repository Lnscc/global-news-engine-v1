package com.example.globalnewsenginev1.gdelt;

import com.example.globalnewsenginev1.articles.ArticleCandidate;
import com.example.globalnewsenginev1.articles.ArticleProjectionService;
import com.example.globalnewsenginev1.gdelt.model.GdeltGkg;
import com.example.globalnewsenginev1.gdelt.repository.GdeltGkgRepository;
import com.example.globalnewsenginev1.ingestion.IngestionJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Order(200)
@ConditionalOnProperty(prefix = "gdelt.ingestion", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GdeltArticleProjectionJob implements IngestionJob {

    private static final Logger log = LoggerFactory.getLogger(GdeltArticleProjectionJob.class);

    private final GdeltGkgRepository gkgRepository;
    private final ArticleProjectionService articleProjectionService;

    public GdeltArticleProjectionJob(
            GdeltGkgRepository gkgRepository,
            ArticleProjectionService articleProjectionService
    ) {
        this.gkgRepository = gkgRepository;
        this.articleProjectionService = articleProjectionService;
    }

    @Override
    public void runIngestion() {
        run();
    }

    @Transactional
    public int run() {
        List<GdeltGkg> rows = gkgRepository.findUnprojectedArticleRows();
        if (rows.isEmpty()) {
            log.info("No normalized GDELT GKG rows are ready for article projection");
            return 0;
        }

        int projected = 0;
        int skipped = 0;
        for (GdeltGkg row : rows) {
            boolean saved = articleProjectionService.project(toArticleCandidate(row));
            row.markArticleProjected();
            if (saved) {
                projected++;
            } else {
                skipped++;
            }
        }

        log.info("Projected {} article row(s) from {} GDELT GKG rows; {} row(s) were skipped", projected, rows.size(), skipped);
        return projected;
    }

    private ArticleCandidate toArticleCandidate(GdeltGkg row) {
        return new ArticleCandidate(
                row.getId(),
                row.getSourceBatch(),
                row.getDate(),
                row.getSourceCommonName(),
                row.getDocumentIdentifier(),
                row.getThemes(),
                row.getPersons(),
                row.getOrganizations(),
                row.getTone(),
                row.getPositiveScore(),
                row.getNegativeScore(),
                row.getPolarity(),
                row.getWordCount(),
                row.getSharingImage(),
                row.getRelatedImages(),
                row.getExtras()
        );
    }
}
