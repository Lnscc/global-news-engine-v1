package com.example.globalnewsenginev1.articles;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArticleProjectionService {

    private final ArticleRepository articleRepository;

    public ArticleProjectionService(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    @Transactional
    public boolean project(ArticleCandidate candidate) {
        String canonicalUrl = ArticleUrlNormalizer.normalize(candidate.documentIdentifier()).orElse(null);
        if (canonicalUrl == null) {
            return false;
        }

        Article article = articleRepository.findByCanonicalUrl(canonicalUrl)
                .map(existing -> {
                    existing.recordSeen(candidate);
                    return existing;
                })
                .orElseGet(() -> new Article(canonicalUrl, candidate));

        articleRepository.save(article);
        return true;
    }
}
