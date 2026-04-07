ALTER TABLE news_content_blocks   ADD COLUMN image_url     VARCHAR(2048);
ALTER TABLE course_content_blocks ADD COLUMN image_url     VARCHAR(2048);
ALTER TABLE news                  ADD COLUMN thumbnail_url VARCHAR(2048);
ALTER TABLE courses               ADD COLUMN thumbnail_url VARCHAR(2048);
