-- Cele treningowe zawodnika: dokładnie JEDEN aktywny cel na horyzont
-- (SHORT/MEDIUM/LONG = krótko-/średnio-/długoterminowy) — max 3 aktywne karty nad kalendarzem.
-- Trener zarządza celami (tworzenie/edycja/usuwanie TYLKO aktywnych + oznaczenie osiągnięcia);
-- zawodnik tylko czyta. Cele osiągnięte (achieved_at) NIGDY nie są usuwane — zasilają
-- "skrzynię trofeów" (puchar mały/średni/duży wg horyzontu).
CREATE TABLE athlete_goals (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    athlete_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    horizon     VARCHAR(10) NOT NULL CHECK (horizon IN ('SHORT', 'MEDIUM', 'LONG')),
    content     VARCHAR(500) NOT NULL,
    target_date DATE NOT NULL,
    achieved_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Twarda gwarancja "jeden aktywny cel na horyzont" (osiągnięte nie blokują slotu).
CREATE UNIQUE INDEX idx_athlete_goals_active ON athlete_goals(athlete_id, horizon) WHERE achieved_at IS NULL;

-- Baner + skrzynia trofeów ładują wszystkie cele jednego zawodnika.
CREATE INDEX idx_athlete_goals_athlete ON athlete_goals(athlete_id);
