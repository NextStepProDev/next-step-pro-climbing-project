CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE TABLE user_news_stars (
    user_id  UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    news_id  UUID        NOT NULL REFERENCES news(id)  ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, news_id)
);

CREATE INDEX idx_user_news_stars_user ON user_news_stars(user_id);
