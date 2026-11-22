-- A GALLERY content block in a news article: it points at an existing album instead of holding
-- copies of its photos. The photos are read from the gallery at render time, so an album shared
-- by an article and the gallery page is uploaded once, and every language version of the article
-- shows the same album (albums have no language).
--
-- ON DELETE SET NULL, not CASCADE: deleting an album must not make a block vanish from the
-- article editor without a trace. The block stays, shows the admin that its album is gone, and is
-- skipped on the public page and in the newsletter.
ALTER TABLE news_content_blocks
    ADD COLUMN album_id UUID NULL REFERENCES albums(id) ON DELETE SET NULL;

-- Only a gallery block may point at an album. The reverse (a gallery block always has one) cannot
-- be a constraint, because SET NULL above is exactly what produces a gallery block without one.
ALTER TABLE news_content_blocks
    ADD CONSTRAINT chk_news_content_blocks_album_only_on_gallery
        CHECK (album_id IS NULL OR block_type = 'GALLERY');

CREATE INDEX idx_news_content_blocks_album_id ON news_content_blocks (album_id)
    WHERE album_id IS NOT NULL;
