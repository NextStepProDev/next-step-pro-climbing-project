ALTER TABLE users ADD COLUMN newsletter_choice_made BOOLEAN NOT NULL DEFAULT FALSE;
-- Mark all existing users as having already decided (don't show them the modal)
UPDATE users SET newsletter_choice_made = TRUE;
