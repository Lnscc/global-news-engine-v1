package com.example.globalnewsenginev1.articles;

import com.example.globalnewsenginev1.gdelt.GdeltGkg;
import com.example.globalnewsenginev1.gdelt.GdeltGkgRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class ArticleProjectionJob {

    private static final Logger log = LoggerFactory.getLogger(ArticleProjectionJob.class);

    private final GdeltGkgRepository gkgRepository;
    private final ArticleRepository articleRepository;
    private final int maxRowsPerRun;

    public ArticleProjectionJob(
            GdeltGkgRepository gkgRepository,
            ArticleRepository articleRepository,
            @Value("${articles.project.max-rows-per-run:1000}") int maxRowsPerRun
    ) {
        this.gkgRepository = gkgRepository;
        this.articleRepository = articleRepository;
        this.maxRowsPerRun = maxRowsPerRun;
    }

    @Transactional
    public int run() {
        List<GdeltGkg> rows = gkgRepository.findUnprojectedArticleRows(PageRequest.of(0, maxRowsPerRun));
        if (rows.isEmpty()) {
            log.info("No normalized GDELT GKG rows are ready for article projection");
            return 0;
        }

        int projected = 0;
        int skipped = 0;
        for (GdeltGkg row : rows) {
            String canonicalUrl = ArticleUrlNormalizer.normalize(row.getDocumentIdentifier()).orElse(null);
            if (canonicalUrl == null) {
                row.markArticleProjected();
                skipped++;
                continue;
            }

            Article article = articleRepository.findByCanonicalUrl(canonicalUrl)
                    .map(existing -> {
                        existing.recordSeen(row);
                        return existing;
                    })
                    .orElseGet(() -> new Article(canonicalUrl, row));

            articleRepository.save(article);
            row.markArticleProjected();
            projected++;
        }

        log.info("Projected {} article row(s) from {} GDELT GKG rows; {} row(s) were skipped", projected, rows.size(), skipped);
        return projected;
    }
}
