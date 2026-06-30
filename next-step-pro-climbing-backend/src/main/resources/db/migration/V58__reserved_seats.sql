-- Miejsca "na zaproszenie": admin trzyma miejsce w slocie lub wydarzeniu dla konkretnego
-- zarejestrowanego użytkownika. Trzymane miejsce liczy się jako zajęte dla wszystkich poza
-- wskazaną osobą — publika widzi "brak wolnych", a zaproszony po zalogowaniu może je zająć.
--
-- Dokładnie jedno z (time_slot_id, event_id) jest ustawione: slotowe vs wydarzeniowe zaproszenie.
-- ON DELETE CASCADE — usunięcie slotu/wydarzenia/użytkownika sprząta powiązane zaproszenia.
CREATE TABLE reserved_seats (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    time_slot_id UUID REFERENCES time_slots(id) ON DELETE CASCADE,
    event_id    UUID REFERENCES events(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT reserved_seats_target_check CHECK (
        (time_slot_id IS NOT NULL AND event_id IS NULL)
        OR (time_slot_id IS NULL AND event_id IS NOT NULL)
    )
);

-- Jedno zaproszenie per (slot, użytkownik) i per (wydarzenie, użytkownik).
CREATE UNIQUE INDEX ux_reserved_seats_slot_user ON reserved_seats(time_slot_id, user_id)
    WHERE time_slot_id IS NOT NULL;
CREATE UNIQUE INDEX ux_reserved_seats_event_user ON reserved_seats(event_id, user_id)
    WHERE event_id IS NOT NULL;

-- Wyszukiwanie zaproszeń danego użytkownika (kalendarz: czy widz ma trzymane miejsce).
CREATE INDEX idx_reserved_seats_user ON reserved_seats(user_id);
