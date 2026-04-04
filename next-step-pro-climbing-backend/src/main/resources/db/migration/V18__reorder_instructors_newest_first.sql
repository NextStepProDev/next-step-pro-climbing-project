-- Reset display_order for existing instructors so newest appears first.
-- Most recently created instructor gets 0, older ones get 1, 2, 3...
-- New instructors created after this migration will receive min - 1, keeping them at the top.
WITH ranked AS (
    SELECT id, (ROW_NUMBER() OVER (ORDER BY created_at DESC) - 1) AS new_order
    FROM instructors
)
UPDATE instructors
SET display_order = ranked.new_order
FROM ranked
WHERE instructors.id = ranked.id;
