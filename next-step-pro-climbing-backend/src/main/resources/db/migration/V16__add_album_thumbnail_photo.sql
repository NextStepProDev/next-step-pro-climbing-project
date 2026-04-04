-- Add explicit thumbnail reference to albums.
-- ON DELETE SET NULL: if the chosen photo is deleted, album falls back to auto-selection (first by display_order).
ALTER TABLE albums
    ADD COLUMN thumbnail_photo_id UUID REFERENCES photos(id) ON DELETE SET NULL;

-- Partial index: only indexes rows where thumbnail is explicitly set (keeps index small)
CREATE INDEX idx_albums_thumbnail_photo ON albums(thumbnail_photo_id)
    WHERE thumbnail_photo_id IS NOT NULL;
