-- Zwiększ długość kolumny block_type w news_content_blocks aby pomieścić 'VIDEO_EMBED' (11 znaków)
ALTER TABLE news_content_blocks
    ALTER COLUMN block_type TYPE VARCHAR(20);
