-- Cele wagowe: drugi RODZAJ celu obok treningowych (osobny rząd kart w banerze).
-- Zamykają się AUTOMATYCZNIE przy ważeniu, ale tylko wtedy, gdy trend 7-dniowy opiera się
-- o co najmniej 3 pomiary z ostatnich 7 dni (WeightTrendCalculator.MIN_SAMPLES_FOR_GOAL) —
-- pojedyncze ważenie po tygodniu przerwy nie ma prawa zamknąć celu.
--
-- AMENDMENT do komentarza z V67 ("cel osiągnięty jest niezmienny na zawsze"): cel zamknięty
-- AUTOMATYCZNIE może zostać cofnięty przez trenera (achieved_automatically = ratunek po
-- literówce w pomiarze, np. 86 wpisane jako 68). Cel oznaczony RĘCZNIE pozostaje
-- nienaruszalny dokładnie jak dotąd.
--
-- Auto-zamknięcia NIE trafiają do activity_logs — nie ma tam aktora (wpis wymaga User),
-- a ślad audytowy niesie para achieved_automatically + achieved_at.
ALTER TABLE athlete_goals
    ADD COLUMN kind                   VARCHAR(10) NOT NULL DEFAULT 'GENERAL',
    ADD COLUMN target_weight_kg       NUMERIC(5,2),
    ADD COLUMN start_weight_kg        NUMERIC(5,2),
    ADD COLUMN achieved_automatically BOOLEAN     NOT NULL DEFAULT false;

ALTER TABLE athlete_goals
    ADD CONSTRAINT chk_athlete_goals_kind CHECK (kind IN ('GENERAL', 'WEIGHT'));

-- Obie kolumny wagowe ustawione WTEDY I TYLKO WTEDY, gdy kind = 'WEIGHT'.
-- start_weight_kg to migawka trendu z chwili założenia celu — zasila pasek postępu,
-- więc cel wagowy bez niej byłby bezużyteczny.
ALTER TABLE athlete_goals
    ADD CONSTRAINT chk_athlete_goals_weight_fields CHECK (
        (kind =  'WEIGHT' AND target_weight_kg IS NOT NULL AND start_weight_kg IS NOT NULL)
     OR (kind <> 'WEIGHT' AND target_weight_kg IS     NULL AND start_weight_kg IS     NULL));

ALTER TABLE athlete_goals
    ADD CONSTRAINT chk_athlete_goals_weight_range CHECK (
        (target_weight_kg IS NULL OR target_weight_kg BETWEEN 20 AND 300)
    AND (start_weight_kg  IS NULL OR start_weight_kg  BETWEEN 20 AND 300));

-- "Zamknięty automatycznie" ma sens wyłącznie dla celu osiągniętego
ALTER TABLE athlete_goals
    ADD CONSTRAINT chk_athlete_goals_auto_achieved
        CHECK (achieved_automatically = false OR achieved_at IS NOT NULL);

-- Slot aktywnego celu rozszerzony o rodzaj: zawodnik może mieć JEDNOCZEŚNIE aktywny cel
-- treningowy i wagowy w tym samym horyzoncie (dwa rzędy po 3 karty w banerze).
-- Bez CONCURRENTLY — Flyway trzyma migrację w transakcji, a tabela ma kilkadziesiąt wierszy.
DROP INDEX IF EXISTS idx_athlete_goals_active;
CREATE UNIQUE INDEX idx_athlete_goals_active
    ON athlete_goals (athlete_id, kind, horizon) WHERE achieved_at IS NULL;
