-- Ocena RPE zawodnika dla ODBYTYCH rezerwacji (kalendarz publiczny) — uzupełnia RPE
-- treningów osobistych, żeby statystyki intensywności pokrywały CAŁĄ aktywność.
-- Jedna ocena na rezerwację (UNIQUE), edytowalna (upsert przez PUT). Kasowana z rezerwacją.
CREATE TABLE reservation_rpe (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id UUID NOT NULL UNIQUE REFERENCES reservations(id) ON DELETE CASCADE,
    rpe            INT NOT NULL CHECK (rpe BETWEEN 1 AND 10),
    note           VARCHAR(500),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
