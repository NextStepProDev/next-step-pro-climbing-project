-- Add password-based authentication fields to users table
ALTER TABLE users
ADD COLUMN password_hash VARCHAR(255),
ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT false,
ADD COLUMN email_verified_at TIMESTAMP WITH TIME ZONE;

-- Create auth_tokens table for verification, reset, and refresh tokens
CREATE TABLE auth_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    token_type VARCHAR(30) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Index for efficient token lookup by hash and type
CREATE INDEX idx_auth_tokens_hash_type ON auth_tokens(token_hash, token_type);

-- Index for cleanup of expired tokens
CREATE INDEX idx_auth_tokens_expires ON auth_tokens(expires_at);

-- Index for user's tokens
CREATE INDEX idx_auth_tokens_user ON auth_tokens(user_id);

-- Mark existing OAuth users as verified (they authenticated via trusted provider)
UPDATE users SET email_verified = true WHERE oauth_provider IS NOT NULL;
