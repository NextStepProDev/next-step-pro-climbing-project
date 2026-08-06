-- Slot marked as "unavailable" — the mirror of EventType.UNAVAILABLE, but for a part of a day.
-- Not the same thing as is_blocked: blocking cancels an existing slot (and mails its participants),
-- an unavailable slot is created closed and never had anyone signed up.
ALTER TABLE time_slots ADD COLUMN is_unavailable BOOLEAN NOT NULL DEFAULT FALSE;

-- A slot has exactly one shape. An availability window invites proposals, an unavailable slot
-- refuses everything — asking for both is a contradiction, not a combination.
ALTER TABLE time_slots ADD CONSTRAINT time_slots_single_kind
    CHECK (NOT (is_availability_window AND is_unavailable));

-- Zero seats is what makes the slot unbookable everywhere at once (status, reservation guards,
-- day counters), instead of every caller having to remember the flag.
ALTER TABLE time_slots ADD CONSTRAINT time_slots_unavailable_has_no_seats
    CHECK (NOT is_unavailable OR max_participants = 0);
