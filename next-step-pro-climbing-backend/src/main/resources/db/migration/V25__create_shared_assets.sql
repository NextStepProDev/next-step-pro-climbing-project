CREATE TABLE shared_assets
(
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    filename      VARCHAR(255) NOT NULL UNIQUE,
    original_name VARCHAR(255) NOT NULL,
    mime_type     VARCHAR(50)  NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    created_at    TIMESTAMP    NOT NULL    DEFAULT NOW()
);
