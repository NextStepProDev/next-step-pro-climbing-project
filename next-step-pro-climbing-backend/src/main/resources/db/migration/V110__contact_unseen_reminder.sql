-- One mail reminder when an answer from the team has waited unseen in an account holder's chat for
-- 48 h (decision 2026-11-25). The chat itself still never mails an account; the reminder carries
-- no content, only "there is an answer waiting" and a link.
--
-- When the reminder went out. Stored, not inferred from a time window: an hourly job that only
-- looked at "answers between 48 and 49 hours old" would silently skip every reminder due during a
-- restart or a deploy. Compared with user_seen_at, it also gives "one reminder until the person
-- opens the chat": several messages from the team, or more answers after the reminder, do not
-- mail again until the person has looked in and then left a NEW answer unread for 48 h.
ALTER TABLE contact_conversations ADD COLUMN unseen_reminder_sent_at TIMESTAMPTZ;
