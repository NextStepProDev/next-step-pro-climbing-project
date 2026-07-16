-- Osobiste treningi zawodnika (styl TrainingPeaks): wspólny plan zawodnika i trenera.
-- Trening dodaje zawodnik LUB trener (created_by_admin) do kalendarza zawodnika;
-- obaj mogą edytować/usuwać dowolny wpis (last_modified_by_admin = kto edytował ostatnio,
-- używane do liczników "nowe od trenera" po stronie zawodnika).
--
-- Wykonanie: completed_at + opcjonalny feedback i RPE 1-10 (skala zmęczenia).
-- Status "pominięty" NIE jest zapisywany — wyliczany: completed_at IS NULL i koniec w przeszłości.
CREATE TABLE personal_trainings (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    athlete_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    training_date          DATE NOT NULL,
    start_time             TIME NOT NULL,
    end_time               TIME NOT NULL,
    title                  VARCHAR(150) NOT NULL,
    description            VARCHAR(2000),
    created_by_admin       BOOLEAN NOT NULL DEFAULT false,
    last_modified_by_admin BOOLEAN NOT NULL DEFAULT false,
    completed_at           TIMESTAMPTZ,
    feedback               VARCHAR(2000),
    rpe                    INT CHECK (rpe BETWEEN 1 AND 10),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Kalendarz ładuje zakres dat jednego zawodnika (widok miesiąca/tygodnia).
CREATE INDEX idx_personal_trainings_athlete_date ON personal_trainings(athlete_id, training_date);
