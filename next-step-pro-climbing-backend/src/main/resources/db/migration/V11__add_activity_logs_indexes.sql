CREATE INDEX idx_activity_logs_user ON activity_logs(user_id);
CREATE INDEX idx_activity_logs_time_slot ON activity_logs(time_slot_id);
CREATE INDEX idx_activity_logs_event ON activity_logs(event_id);
