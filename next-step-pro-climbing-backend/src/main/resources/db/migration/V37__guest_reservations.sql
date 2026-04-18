CREATE TABLE guest_reservations (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    time_slot_id UUID         REFERENCES time_slots(id) ON DELETE CASCADE,
    event_id     UUID         REFERENCES events(id)     ON DELETE CASCADE,
    note         VARCHAR(500) NOT NULL,
    participants INT          NOT NULL DEFAULT 1,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_guest_reservation_target CHECK (
        (time_slot_id IS NOT NULL AND event_id IS NULL)
        OR (time_slot_id IS NULL AND event_id IS NOT NULL)
    )
);

CREATE INDEX idx_guest_reservations_slot  ON guest_reservations(time_slot_id);
CREATE INDEX idx_guest_reservations_event ON guest_reservations(event_id);
