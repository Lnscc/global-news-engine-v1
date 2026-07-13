CREATE TABLE gdelt_gkg_themes (
    gkg_record_id BIGINT NOT NULL REFERENCES gdelt_gkg_records(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    theme TEXT NOT NULL,
    CONSTRAINT pk_gdelt_gkg_themes PRIMARY KEY (gkg_record_id, theme),
    CONSTRAINT ck_gdelt_gkg_themes_position CHECK (position >= 0),
    CONSTRAINT ck_gdelt_gkg_themes_not_blank CHECK (TRIM(theme) <> '')
);

CREATE INDEX idx_gdelt_gkg_themes_theme ON gdelt_gkg_themes (theme);

INSERT INTO gdelt_gkg_themes (gkg_record_id, position, theme)
WITH RECURSIVE split_themes (gkg_record_id, remaining, position, theme) AS (
    SELECT id, themes_raw || ';', -1, CAST(NULL AS TEXT)
    FROM gdelt_gkg_records
    WHERE themes_raw IS NOT NULL AND TRIM(themes_raw) <> ''

    UNION ALL

    SELECT gkg_record_id,
           SUBSTRING(remaining FROM POSITION(';' IN remaining) + 1),
           position + 1,
           TRIM(SUBSTRING(remaining FROM 1 FOR POSITION(';' IN remaining) - 1))
    FROM split_themes
    WHERE POSITION(';' IN remaining) > 0
), normalized_themes (gkg_record_id, position, theme) AS (
    SELECT gkg_record_id, MIN(position) AS position, theme
    FROM split_themes
    WHERE theme IS NOT NULL AND theme <> ''
    GROUP BY gkg_record_id, theme
)
SELECT gkg_record_id, position, theme
FROM normalized_themes;
