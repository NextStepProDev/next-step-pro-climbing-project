-- Add focal point columns to instructors table for photo crop positioning
ALTER TABLE instructors
    ADD COLUMN focal_point_x REAL,
    ADD COLUMN focal_point_y REAL;
