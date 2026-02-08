-- User notification preferences
ALTER TABLE users ADD COLUMN email_notifications_enabled BOOLEAN NOT NULL DEFAULT true;

-- Optional comment on reservation
ALTER TABLE reservations ADD COLUMN comment VARCHAR(500);
