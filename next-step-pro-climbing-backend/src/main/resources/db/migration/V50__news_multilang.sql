ALTER TABLE news ADD COLUMN language VARCHAR(5) NOT NULL DEFAULT 'pl';
ALTER TABLE news ADD COLUMN translation_group_id UUID;
UPDATE news SET translation_group_id = id;
ALTER TABLE news ALTER COLUMN translation_group_id SET NOT NULL;
CREATE UNIQUE INDEX idx_news_translation_lang ON news (translation_group_id, language);
CREATE INDEX idx_news_lang_published ON news (language, is_published);
