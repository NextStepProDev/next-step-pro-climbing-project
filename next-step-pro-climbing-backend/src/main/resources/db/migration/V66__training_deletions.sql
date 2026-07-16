-- Rejestr usunięć PRZYSZŁYCH treningów osobistych (kalendarz treningowy).
-- Usunięty wiersz personal_trainings znika, więc liczniki "nowe" (COUNT po timestampach
-- vs znacznik seen) nie mają czego policzyć — stąd osobny, mały log zdarzeń.
-- Wpis powstaje TYLKO gdy usuwany trening jeszcze się nie zaczął (czas Warsaw):
--   - deleted_by_admin = true  -> alert dla zawodnika (trener wyciął mu plan),
--   - deleted_by_admin = false -> alert dla trenera (zawodnik wypisał trening z planu).
-- Migawka danych treningu (tytuł/termin) zostaje tutaj, bo oryginał już nie istnieje.
-- Wpisy starsze niż 60 dni są przycinane przy okazji kolejnych usunięć.
CREATE TABLE training_deletions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    athlete_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title            VARCHAR(150) NOT NULL,
    training_date    DATE NOT NULL,
    start_time       TIME NOT NULL,
    end_time         TIME NOT NULL,
    deleted_by_admin BOOLEAN NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_training_deletions_athlete ON training_deletions(athlete_id);
