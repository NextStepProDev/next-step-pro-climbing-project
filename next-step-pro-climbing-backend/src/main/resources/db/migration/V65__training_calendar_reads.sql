-- Znacznik "przeczytane" dla kalendarza treningowego, w OBU kierunkach jedną tabelą
-- (wzorzec jak users.admin_reservations_seen_at z V60, ale per para oglądający/kalendarz):
--   - zawodnik: wiersz (user_id = athlete_id = własne id) — kiedy ostatnio otworzył swój kalendarz,
--   - trener:   wiersz per (admin, zawodnik) — wielu adminów ma niezależne liczniki.
-- Liczniki "nowe" to COUNT-y po timestampach personal_trainings/training_comments > seen_at;
-- brak wiersza = licz wszystko (nowy kalendarz i tak startuje pusty). Upsert przy mark-seen.
CREATE TABLE training_calendar_reads (
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    athlete_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    seen_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, athlete_id)
);
