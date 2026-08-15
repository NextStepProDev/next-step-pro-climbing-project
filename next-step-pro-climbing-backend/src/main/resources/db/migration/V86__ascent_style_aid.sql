-- A0 — przejście z użyciem punktów sztucznych (podciągnięcie na przelocie, stanięcie w pętli).
-- Najsłabszy styl, dostępny WYŁĄCZNIE w górach: długą drogę alpejską przejechaną przez jedno
-- miejsce nadal się przeszło, podczas gdy ten sam dzień na skałce uczciwie zapisuje się jako
-- próbę RP, a nie jako przejście.
ALTER TABLE climbing_ascents DROP CONSTRAINT chk_climbing_ascents_style;
ALTER TABLE climbing_ascents DROP CONSTRAINT chk_climbing_ascents_mountain_style;

ALTER TABLE climbing_ascents
    ADD CONSTRAINT chk_climbing_ascents_style
        CHECK (style IN ('OS', 'FLASH', 'RP', 'TR', 'SOLO', 'FREE_SOLO', 'A0')),

    -- Góry: bez wędki, za to z A0.
    ADD CONSTRAINT chk_climbing_ascents_mountain_style CHECK (
        terrain <> 'MOUNTAIN' OR style IN ('OS', 'FLASH', 'RP', 'SOLO', 'FREE_SOLO', 'A0')
    ),

    -- A0 nie ma sensu na skałce: tam ten sam dzień to próba, nie przejście.
    ADD CONSTRAINT chk_climbing_ascents_rock_style
        CHECK (terrain <> 'ROCK' OR style <> 'A0');
