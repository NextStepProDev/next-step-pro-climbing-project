-- Add account lockout fields to users table
-- This helps protect against brute-force attacks

ALTER TABLE users ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN locked_until TIMESTAMPTZ;

-- Index for efficient lookup of locked accounts
CREATE INDEX idx_users_locked_until ON users(locked_until);
