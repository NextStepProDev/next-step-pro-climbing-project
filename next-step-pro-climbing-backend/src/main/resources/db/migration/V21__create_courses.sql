CREATE TABLE courses
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

CREATE INDEX idx_courses_published ON courses (is_published, published_at DESC);
CREATE INDEX idx_courses_created_at ON courses (created_at DESC);

CREATE TABLE course_content_blocks
(
    id             UUID PRIMARY KEY,
    course_id      UUID                     NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    block_type     VARCHAR(10)              NOT NULL,
    content        TEXT,
    image_filename VARCHAR(500),
    caption        TEXT,
    display_order  INTEGER                  NOT NULL DEFAULT 0,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_course_blocks_course ON course_content_blocks (course_id, display_order ASC);
