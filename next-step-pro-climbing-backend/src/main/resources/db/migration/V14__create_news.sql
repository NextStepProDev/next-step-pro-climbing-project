CREATE TABLE news
(
    id                 UUID PRIMARY KEY,
    title              VARCHAR(500)             NOT NULL,
    excerpt            TEXT,
    thumbnail_filename VARCHAR(500),
    is_published       BOOLEAN                  NOT NULL DEFAULT false,
    published_at       TIMESTAMP WITH TIME ZONE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_news_published ON news (is_published, published_at DESC);
CREATE INDEX idx_news_created_at ON news (created_at DESC);

CREATE TABLE news_content_blocks
(
    id               UUID PRIMARY KEY,
    news_id          UUID                     NOT NULL REFERENCES news (id) ON DELETE CASCADE,
    block_type       VARCHAR(10)              NOT NULL,
    content          TEXT,
    image_filename   VARCHAR(500),
    caption          TEXT,
    display_order    INTEGER                  NOT NULL DEFAULT 0,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_news_blocks_news ON news_content_blocks (news_id, display_order ASC);
