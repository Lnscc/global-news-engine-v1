CREATE TABLE article_enrichments (
    article_id BIGINT PRIMARY KEY REFERENCES articles(id) ON DELETE CASCADE,
    title TEXT,
    published_at TIMESTAMP WITH TIME ZONE,
    language VARCHAR(64),
    main_image_url TEXT,
    extracted_text TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    error_code VARCHAR(64),
    error_message VARCHAR(2000),
    enriched_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_article_enrichments_status
        CHECK (status = 'PENDING' OR status = 'PROCESSING'
               OR status = 'SUCCEEDED' OR status = 'FAILED'),
    CONSTRAINT chk_article_enrichments_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT chk_article_enrichments_processing
        CHECK (status <> 'PROCESSING' OR last_attempt_at IS NOT NULL),
    CONSTRAINT chk_article_enrichments_succeeded
        CHECK (status <> 'SUCCEEDED' OR
               (enriched_at IS NOT NULL AND next_attempt_at IS NULL
                AND error_code IS NULL AND error_message IS NULL)),
    CONSTRAINT chk_article_enrichments_failed
        CHECK (status <> 'FAILED' OR (error_code IS NOT NULL AND error_message IS NOT NULL)),
    CONSTRAINT chk_article_enrichments_retry_state
        CHECK (next_attempt_at IS NULL OR status = 'FAILED')
);

CREATE INDEX idx_article_enrichments_due
    ON article_enrichments (status, next_attempt_at);
