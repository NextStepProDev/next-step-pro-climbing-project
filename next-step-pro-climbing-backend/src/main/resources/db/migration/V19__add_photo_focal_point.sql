-- Add focal point columns to photos table for manual thumbnail crop positioning
ALTER TABLE photos
    ADD COLUMN focal_point_x REAL,
    ADD COLUMN focal_point_y REAL;
