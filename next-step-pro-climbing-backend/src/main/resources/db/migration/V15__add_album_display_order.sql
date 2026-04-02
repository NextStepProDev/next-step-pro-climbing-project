ALTER TABLE albums ADD COLUMN display_order INTEGER NOT NULL DEFAULT 0;

-- Initialize existing albums with ascending order based on current created_at DESC
-- (preserves the visual order the admin already sees)
WITH ranked AS (
    SELECT id, (ROW_NUMBER() OVER (ORDER BY created_at DESC) - 1) AS rn
    FROM albums
)
UPDATE albums SET display_order = ranked.rn
FROM ranked
WHERE albums.id = ranked.id;

-- Replace the existing created_at index with a display_order index
DROP INDEX IF EXISTS idx_albums_created_at;
CREATE INDEX idx_albums_display_order ON albums(display_order ASC);
