package com.example.globalnewsenginev1.articles.enrichment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Repository
public class ArticleEnrichmentRepository {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public ArticleEnrichmentRepository(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    public boolean enqueue(long articleId, Instant now) {
        return jdbcTemplate.update("""
                INSERT INTO article_enrichments
                    (article_id, status, attempt_count, created_at, updated_at)
                SELECT ?, 'PENDING', 0, ?, ?
                WHERE EXISTS (SELECT 1 FROM articles WHERE id = ?)
                  AND NOT EXISTS (SELECT 1 FROM article_enrichments WHERE article_id = ?)
                """, articleId, utc(now), utc(now), articleId, articleId) == 1;
    }

    public int enqueueMissing(int batchSize, Instant now) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than zero");
        }
        return transactionTemplate.execute(status -> {
            List<Long> articleIds = jdbcTemplate.queryForList("""
                    SELECT article.id
                    FROM articles article
                    WHERE NOT EXISTS (
                        SELECT 1 FROM article_enrichments enrichment
                        WHERE enrichment.article_id = article.id
                    )
                    ORDER BY article.id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                    """, Long.class, batchSize);
            int inserted = 0;
            for (Long articleId : articleIds) {
                inserted += jdbcTemplate.update("""
                        INSERT INTO article_enrichments
                            (article_id, status, attempt_count, created_at, updated_at)
                        VALUES (?, 'PENDING', 0, ?, ?)
                        """, articleId, utc(now), utc(now));
            }
            return inserted;
        });
    }

    public List<ClaimedArticleEnrichment> claimDue(int batchSize, Instant now) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be greater than zero");
        }
        return transactionTemplate.execute(status -> {
            List<ClaimedArticleEnrichment> claims = jdbcTemplate.query("""
                    SELECT enrichment.article_id, article.canonical_url, enrichment.attempt_count
                    FROM article_enrichments enrichment
                    JOIN articles article ON article.id = enrichment.article_id
                    WHERE enrichment.status = 'PENDING'
                       OR (enrichment.status = 'FAILED' AND enrichment.next_attempt_at <= ?)
                    ORDER BY enrichment.article_id
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                    """, (resultSet, rowNumber) -> new ClaimedArticleEnrichment(
                            resultSet.getLong("article_id"),
                            resultSet.getString("canonical_url"),
                            resultSet.getInt("attempt_count") + 1),
                    utc(now), batchSize);
            if (claims.isEmpty()) {
                return List.of();
            }

            String placeholders = String.join(",", claims.stream().map(ignored -> "?").toList());
            List<Object> arguments = new ArrayList<>();
            arguments.add(utc(now));
            arguments.add(utc(now));
            claims.forEach(claim -> arguments.add(claim.articleId()));
            jdbcTemplate.update("""
                    UPDATE article_enrichments
                    SET status = 'PROCESSING', attempt_count = attempt_count + 1,
                        last_attempt_at = ?, next_attempt_at = NULL,
                        error_code = NULL, error_message = NULL, updated_at = ?
                    WHERE article_id IN (%s)
                    """.formatted(placeholders), arguments.toArray());
            return List.copyOf(claims);
        });
    }

    public int markSucceeded(long articleId, ArticleEnrichmentResult result, Instant now) {
        return jdbcTemplate.update("""
                UPDATE article_enrichments
                SET title = ?, published_at = ?, language = ?, main_image_url = ?, extracted_text = ?,
                    status = 'SUCCEEDED', next_attempt_at = NULL, error_code = NULL,
                    error_message = NULL, enriched_at = ?, updated_at = ?
                WHERE article_id = ? AND status = 'PROCESSING'
                """, result.title(), utc(result.publishedAt()), result.language(), result.mainImageUrl(),
                result.extractedText(), utc(now), utc(now), articleId);
    }

    public int markFailed(long articleId, String errorCode, String errorMessage, Instant nextAttemptAt, Instant now) {
        if (errorCode == null || errorCode.isBlank() || errorMessage == null || errorMessage.isBlank()) {
            throw new IllegalArgumentException("errorCode and errorMessage are required");
        }
        String limitedMessage = errorMessage.length() <= 2000 ? errorMessage : errorMessage.substring(0, 2000);
        return jdbcTemplate.update("""
                UPDATE article_enrichments
                SET status = 'FAILED', next_attempt_at = ?, error_code = ?, error_message = ?, updated_at = ?
                WHERE article_id = ? AND status = 'PROCESSING'
                """, utc(nextAttemptAt), errorCode, limitedMessage, utc(now), articleId);
    }

    private OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
