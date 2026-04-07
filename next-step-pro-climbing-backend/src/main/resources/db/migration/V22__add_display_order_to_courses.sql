ALTER TABLE courses ADD COLUMN display_order INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_courses_display_order ON courses (display_order ASC);

-- Ustaw początkową kolejność na podstawie daty dodania
UPDATE courses SET display_order = sub.rn
FROM (
    SELECT id, ROW_NUMBER() OVER (ORDER BY created_at ASC) - 1 AS rn
    FROM courses
) sub
WHERE courses.id = sub.id;
