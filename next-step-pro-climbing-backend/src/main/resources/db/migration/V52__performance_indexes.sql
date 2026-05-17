-- FK index missing from V31 (events.course_id)
CREATE INDEX idx_events_course_id ON events(course_id);

-- Compound index for reservation lookups (user + status)
CREATE INDEX idx_reservations_user_status ON reservations(user_id, status);

-- Newsletter subscriber filter (partial index)
CREATE INDEX idx_users_newsletter ON users(newsletter_subscribed) WHERE newsletter_subscribed = true;
