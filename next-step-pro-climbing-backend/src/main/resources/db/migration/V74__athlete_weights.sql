-- Poranna waga zawodnika: jeden pomiar na dzień (UNIQUE), edytowalny i kasowalny WYŁĄCZNIE
-- przez zawodnika (upsert przez PUT — drugie ważenie tego samego dnia to korekta, nie drugi
-- punkt pomiarowy). Trener ma TYLKO odczyt: to dane o cudzym ciele, więc nie ma endpointu
-- zapisu po stronie admina.
--
-- Trend 7-dniowy liczony jest w aplikacji (WeightTrendCalculator) — baza trzyma wyłącznie
-- surowe pomiary, żeby zmiana definicji trendu nie wymagała migracji danych.
CREATE TABLE athlete_weights (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    athlete_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    measured_on DATE NOT NULL,
    -- Zakres szeroki dla każdego, a wąski na tyle, by złapać zgubiony przecinek (7,42 / 742)
    weight_kg   NUMERIC(5,2) NOT NULL CHECK (weight_kg BETWEEN 20 AND 300),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_athlete_weights_day UNIQUE (athlete_id, measured_on)
);

-- Świadomie BEZ osobnego indeksu (athlete_id, measured_on DESC): uq_athlete_weights_day
-- już zakłada btree na tej parze, a Postgres skanuje btree wstecz tym samym kosztem —
-- drugi indeks byłby czystym narzutem przy zapisie.
