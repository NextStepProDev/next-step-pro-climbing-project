ALTER TABLE instructors ADD COLUMN language VARCHAR(5) NOT NULL DEFAULT 'pl';
ALTER TABLE instructors ADD COLUMN translation_group_id UUID;
UPDATE instructors SET translation_group_id = id;
ALTER TABLE instructors ALTER COLUMN translation_group_id SET NOT NULL;
CREATE UNIQUE INDEX idx_instructors_translation_lang ON instructors (translation_group_id, language);
CREATE INDEX idx_instructors_lang_active ON instructors (language, is_active);
