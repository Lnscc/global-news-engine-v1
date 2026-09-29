ALTER TABLE stories ADD COLUMN public_id UUID;
UPDATE stories SET public_id = id;
ALTER TABLE stories ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE stories ADD CONSTRAINT uq_stories_public_version UNIQUE (clustering_version_id, public_id);
