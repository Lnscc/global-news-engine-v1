package com.example.globalnewsenginev1.articles.api;

import com.example.globalnewsenginev1.articles.query.ArticleDetail;
import com.example.globalnewsenginev1.articles.query.ArticlePage;
import com.example.globalnewsenginev1.articles.query.ArticleQueryService;
import com.example.globalnewsenginev1.articles.query.ArticleSignal;
import com.example.globalnewsenginev1.articles.query.NamedCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ArticleControllerTests {

    private ArticleQueryService queryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        queryService = mock(ArticleQueryService.class);
        mockMvc = standaloneSetup(new ArticleController(queryService))
                .setControllerAdvice(new ArticleApiExceptionHandler())
                .build();
    }

    @Test
    void returnsAnEmptyArticlePageWithDefaults() throws Exception {
        when(queryService.latestArticles(0, 20)).thenReturn(new ArticlePage(List.of(), 0, 20, 0));

        mockMvc.perform(get("/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articles").isEmpty())
                .andExpect(jsonPath("$.offset").value(0))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void returnsArticleDetailAndNotFound() throws Exception {
        Instant timestamp = Instant.parse("2026-07-01T10:00:00Z");
        ArticleSignal signal = new ArticleSignal(
                7, "EVENTS", 9, timestamp, 123L, "042",
                null, null, null, null, -1.5, "-1.5");
        when(queryService.articleDetail(42)).thenReturn(Optional.of(new ArticleDetail(
                42, "https://example.org/article", "example.org", timestamp, timestamp, timestamp,
                List.of(signal))));
        when(queryService.articleDetail(99)).thenReturn(Optional.empty());

        mockMvc.perform(get("/articles/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.canonicalUrl").value("https://example.org/article"))
                .andExpect(jsonPath("$.signals[0].signalType").value("EVENTS"));
        mockMvc.perform(get("/articles/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsTopDomainsAndThemesUsingRequestedLimits() throws Exception {
        when(queryService.topDomains(2)).thenReturn(List.of(new NamedCount("example.org", 3)));
        when(queryService.topThemes(1)).thenReturn(List.of());

        mockMvc.perform(get("/articles/domains/top").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("example.org"))
                .andExpect(jsonPath("$[0].count").value(3));
        mockMvc.perform(get("/articles/themes/top").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(queryService).topDomains(2);
        verify(queryService).topThemes(1);
    }

    @Test
    void rejectsInvalidPaginationAndLimits() throws Exception {
        when(queryService.latestArticles(-1, 20))
                .thenThrow(new IllegalArgumentException("offset must not be negative"));
        when(queryService.topDomains(101))
                .thenThrow(new IllegalArgumentException("limit must be between 1 and 100"));

        mockMvc.perform(get("/articles").param("offset", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_request"));
        mockMvc.perform(get("/articles/domains/top").param("limit", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("limit must be between 1 and 100"));
        mockMvc.perform(get("/articles").param("limit", "not-a-number"))
                .andExpect(status().isBadRequest());
    }
}
