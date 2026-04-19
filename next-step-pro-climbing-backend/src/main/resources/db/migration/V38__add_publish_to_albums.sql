-- Add draft/publish support to albums
-- Existing albums default to published=true so the gallery doesn't break
ALTER TABLE albums
    ADD COLUMN is_published BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN published_at TIMESTAMPTZ;

-- Backfill published_at for existing albums
UPDATE albums SET published_at = created_at;

-- Change default to false so new albums start as drafts
ALTER TABLE albums ALTER COLUMN is_published SET DEFAULT false;

CREATE INDEX idx_albums_published ON albums (is_published, display_order ASC);
