ALTER TABLE gdelt_stage_gkg
    ADD COLUMN page_title VARCHAR(1000);
ALTER TABLE gdelt_stage_gkg
    ADD COLUMN metadata_extracted BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE articles
    ADD COLUMN title VARCHAR(1000);
ALTER TABLE articles
    ADD COLUMN title_source VARCHAR(32);

ALTER TABLE articles
    ADD CONSTRAINT ck_articles_title_source
        CHECK ((title IS NULL AND title_source IS NULL)
            OR (title IS NOT NULL AND title_source IS NOT NULL));
