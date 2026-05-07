-- Add language support for courses (PL, EN, DE)
ALTER TABLE courses ADD COLUMN language VARCHAR(5) NOT NULL DEFAULT 'pl';
ALTER TABLE courses ADD COLUMN translation_group_id UUID;

-- Backfill: each existing course gets its own translation group
UPDATE courses SET translation_group_id = id;

ALTER TABLE courses ALTER COLUMN translation_group_id SET NOT NULL;

-- Prevent two courses with the same language in the same translation group
CREATE UNIQUE INDEX uq_courses_translation_group_language
    ON courses (translation_group_id, language);

-- Speed up public filtered query
CREATE INDEX idx_courses_language_published
    ON courses (language, is_published);
