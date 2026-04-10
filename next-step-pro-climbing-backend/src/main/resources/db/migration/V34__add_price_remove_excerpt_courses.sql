-- V34: Zastępuje pole excerpt polem price w tabeli courses

ALTER TABLE courses DROP COLUMN IF EXISTS excerpt;
ALTER TABLE courses ADD COLUMN price VARCHAR(255);
