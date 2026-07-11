package com.example.globalnewsenginev1.articles.query;

import java.util.List;

public record ArticlePage(
        List<ArticleSummary> articles,
        int offset,
        int limit,
        long total
) {
    public ArticlePage {
        articles = List.copyOf(articles);
    }
}
