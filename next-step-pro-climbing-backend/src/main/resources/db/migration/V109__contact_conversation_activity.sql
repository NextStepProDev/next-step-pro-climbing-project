-- The admin message centre: the team can START a conversation with a registered person, and the
-- list is ordered like a messenger - by the newest message in EITHER direction.
--
-- A conversation the team started has no message from the person yet, so last_inbound_at can no
-- longer be NOT NULL. It still means exactly one thing - "the person's newest message" - and an
-- empty value is "the person has not written", which is not unread for the team.
ALTER TABLE contact_conversations ALTER COLUMN last_inbound_at DROP NOT NULL;

-- Somebody wrote something: the team, the person, or both. A conversation with neither clock
-- would be a row that nothing can order, show or ever light.
ALTER TABLE contact_conversations ADD CONSTRAINT chk_contact_conversations_has_message
    CHECK (last_inbound_at IS NOT NULL OR last_reply_at IS NOT NULL);

-- The ordering column, stored rather than computed as GREATEST(...) in every query: sorting on an
-- expression over a nullable column puts NULLs FIRST under DESC in Postgres, which would pin every
-- conversation the team started above all the others.
ALTER TABLE contact_conversations ADD COLUMN last_activity_at TIMESTAMPTZ;
-- GREATEST ignores NULLs in Postgres, so a conversation never answered takes its inbound time.
UPDATE contact_conversations SET last_activity_at = GREATEST(last_inbound_at, last_reply_at);
ALTER TABLE contact_conversations ALTER COLUMN last_activity_at SET NOT NULL;

-- The list no longer sorts by the inbound clock.
DROP INDEX idx_contact_conversations_last_inbound;
CREATE INDEX idx_contact_conversations_last_activity ON contact_conversations (last_activity_at DESC, id);
