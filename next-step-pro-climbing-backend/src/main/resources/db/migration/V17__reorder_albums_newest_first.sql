-- Reset display_order for existing albums so newest appears first (lowest display_order = top).
-- After this migration: most recently created album gets 0, older ones get 1, 2, 3...
-- New albums created after this migration will receive min - 1, keeping them at the top.
WITH ranked AS (
    SELECT id, (ROW_NUMBER() OVER (ORDER BY created_at DESC) - 1) AS new_order
    FROM albums
)
UPDATE albums
SET display_order = ranked.new_order
FROM ranked
WHERE albums.id = ranked.id;
