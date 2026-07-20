-- Rozszerzenie materiałów treningu o wgrywane pliki (PDF/zdjęcia) obok linków.
-- Wiersz jest ALBO linkiem (kind='LINK', url), ALBO plikiem (kind='FILE', filename) — CHECK pilnuje.
-- Istniejące wiersze z V68 to linki (default 'LINK', url NOT NULL nadal je spełnia).
ALTER TABLE training_attachments ADD COLUMN kind VARCHAR(10) NOT NULL DEFAULT 'LINK'
    CHECK (kind IN ('LINK', 'FILE'));
ALTER TABLE training_attachments ALTER COLUMN url DROP NOT NULL;
ALTER TABLE training_attachments ADD COLUMN filename VARCHAR(255);
ALTER TABLE training_attachments ADD COLUMN original_name VARCHAR(255);
ALTER TABLE training_attachments ADD COLUMN mime_type VARCHAR(100);
ALTER TABLE training_attachments ADD COLUMN size_bytes BIGINT;

ALTER TABLE training_attachments ADD CONSTRAINT chk_training_attachments_owner
    CHECK (
        (kind = 'LINK' AND url IS NOT NULL)
        OR (kind = 'FILE' AND filename IS NOT NULL)
    );
