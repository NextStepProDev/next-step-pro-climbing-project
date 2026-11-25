-- Photos and PDFs in the chat with a signed-in person (decision 2026-11-25). Only in account
-- conversations: a guest is reached by mail alone, and a file would have to travel as a mail
-- attachment - so the signed-out form stays text, and a reply with a file to a guest is refused.
--
-- The same shape as training_comment_files (V80), deliberately a separate table: those belong to
-- the coach/athlete training threads and are swept, exported and erased along those paths.

-- A message may now be only a file, so the text becomes optional - but never blank when present.
ALTER TABLE contact_messages ALTER COLUMN body DROP NOT NULL;
ALTER TABLE contact_messages DROP CONSTRAINT chk_contact_messages_body;
ALTER TABLE contact_messages ADD CONSTRAINT chk_contact_messages_body CHECK (body IS NULL OR btrim(body) <> '');

CREATE TABLE contact_message_files (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- CASCADE removes the row with its message; the bytes on disk are unlinked by the code paths
    -- that delete messages (and caught by the orphan sweep when one is missed).
    message_id       UUID NOT NULL REFERENCES contact_messages(id) ON DELETE CASCADE,
    -- UUID + extension in the private contactfiles/ folder. Unique: one row is one file on disk,
    -- so deleting the row means deleting the file, with no reference counting.
    filename         VARCHAR(64) NOT NULL UNIQUE,
    -- Display only, HTML-escaped on the way in; the name on disk is never the client's.
    original_name    VARCHAR(255),
    mime_type        VARCHAR(100) NOT NULL,
    size_bytes       BIGINT NOT NULL,
    -- Both or neither: a PDF has no dimensions; an image's reserve its space in the chat.
    width            SMALLINT,
    height           SMALLINT,
    position         SMALLINT NOT NULL,
    -- Stored at upload, not derived: the date is shown next to the file, and a later change of the
    -- retention window must not rewrite the fate of a file somebody has already seen dated.
    expires_at       TIMESTAMPTZ NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_contact_message_files_dimensions CHECK ((width IS NULL) = (height IS NULL)),
    CONSTRAINT chk_contact_message_files_size CHECK (size_bytes > 0)
);

CREATE INDEX idx_contact_message_files_message ON contact_message_files (message_id, position);
-- The yearly retention sweep looks files up by expiry.
CREATE INDEX idx_contact_message_files_expires ON contact_message_files (expires_at);
