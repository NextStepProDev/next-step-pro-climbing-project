-- Przejścia górskie obok skalnych, w JEDNEJ tabeli rozróżnianej kolumną terrain.
--
-- Osobna tabela byłaby drugą kopią serwisu, statystyk, kontrolera i eksportu — czyli dokładnie
-- tym bliźniactwem slot/event, które w tym projekcie kosztowało już kilka bugów ("poprawka
-- trafia do jednej kopii"). Wspólne zostaje wszystko, co naprawdę wspólne: oś wycen (skala
-- ujednolicona jest francuska, ta sama co w skałach), styl, data, komentarz, publiczna lista,
-- eksport i przełącznik prywatności. Rozdzielone jest to, co się różni — i pilnują tego CHECK-i,
-- tak samo jak przy TRAINING/TASK w planie treningowym (V77).
--
-- REJONU I SZCZYTU NIE DODAJEMY: to są istniejące area + crag. "Peñón de Ifach / Calpe" →
-- "Peñón de Ifach" to ta sama para co "Jura Północna" → "Turnia Lechfora": miejsce i obiekt
-- w nim. Dzięki temu podpowiedzi z własnej historii, area_key i ranking rejonów działają dla
-- gór bez jednej linii nowego kodu; różni się wyłącznie etykieta w formularzu.
ALTER TABLE climbing_ascents
    -- ROCK (domyślnie, bo wszystkie istniejące wiersze są skalne) albo MOUNTAIN
    ADD COLUMN terrain         VARCHAR(10)  NOT NULL DEFAULT 'ROCK',
    -- Zimowe przejście tej samej drogi to inne przedsięwzięcie — stąd osobna kolumna, a nie
    -- domysł z daty: marzec bywa zimą w Tatrach i latem w Hiszpanii.
    ADD COLUMN winter          BOOLEAN,
    -- Wycena w skali oryginalnej ("V", "UIAA VI", "WI4") — wolny tekst, bo to CYTAT z przewodnika,
    -- nie klucz grupowania. Statystyki liczą wyłącznie po skali ujednoliconej (grade), więc ten
    -- tekst niczego nie sortuje i nie musi być z listy.
    ADD COLUMN original_grade  VARCHAR(40),
    ADD COLUMN length_meters   INTEGER,
    ADD COLUMN pitches         INTEGER,
    -- Czas przejścia w minutach, choć wpisuje się godziny: minuty są jednostką, w której da się
    -- zsumować "6,5 h" i "45 min" bez ułamków w bazie.
    ADD COLUMN duration_minutes INTEGER,
    -- Co poprowadził AUTOR wpisu, a nie co miała droga. Dwuosobowy zespół na 6c+ to dwa różne
    -- przejścia w zależności od tego, kto szedł pierwszy — bez tych kolumn piramida nie umie
    -- odpowiedzieć na "na jakim poziomie prowadzę".
    ADD COLUMN led_grade       VARCHAR(20),
    ADD COLUMN led_pitches     INTEGER,
    -- Jedno pole tekstowe zamiast rozdzielania na konta w systemie i "obcych" (tak robi Sorga,
    -- bo buduje graf przejść między użytkownikami). Tu partner to opis przejścia, nie relacja
    -- w bazie — i nie wymaga, żeby kolega miał konto.
    ADD COLUMN partners        VARCHAR(300);

-- Dyscyplina (sport/boulder/trad) opisuje skałkę, nie górę, więc dla wpisów górskich jest NULL.
ALTER TABLE climbing_ascents ALTER COLUMN discipline DROP NOT NULL;

-- Skala ujednolicona jest francuska także w górach, więc stary CHECK (który wiązał skalę
-- WYŁĄCZNIE z dyscypliną) przestaje wystarczać: wpis górski nie ma dyscypliny w rozumieniu
-- sport/boulder/trad. Przebudowa, nie modyfikacja — starej migracji nie wolno tknąć.
ALTER TABLE climbing_ascents DROP CONSTRAINT chk_climbing_ascents_grade_scale;
ALTER TABLE climbing_ascents DROP CONSTRAINT chk_climbing_ascents_boulder_style;

ALTER TABLE climbing_ascents
    ADD CONSTRAINT chk_climbing_ascents_terrain
        CHECK (terrain IN ('ROCK', 'MOUNTAIN')),

    -- Kształt wpisu skalnego: ma dyscyplinę, nie ma pól górskich.
    ADD CONSTRAINT chk_climbing_ascents_rock_shape CHECK (
        terrain <> 'ROCK' OR (
            discipline IS NOT NULL
            AND winter IS NULL AND original_grade IS NULL AND length_meters IS NULL
            AND pitches IS NULL AND duration_minutes IS NULL
            AND led_grade IS NULL AND led_pitches IS NULL AND partners IS NULL
        )
    ),

    -- Kształt wpisu górskiego: sezon obowiązkowy, dyscypliny (sport/boulder/trad) nie ma,
    -- bo w górach rozróżnia sezon, a nie rodzaj skałki. Nie ma też liczby prób i gwiazdek —
    -- projekt w górach nie działa jak projekt w skałach, a ocena drogi to skalna konwencja.
    ADD CONSTRAINT chk_climbing_ascents_mountain_shape CHECK (
        terrain <> 'MOUNTAIN' OR (
            discipline IS NULL AND winter IS NOT NULL
            AND attempts IS NULL AND quality_stars IS NULL
        )
    ),

    -- Oś wyceny: boulder na Font, reszta (skała sportowa/trad ORAZ całe góry) na francuskiej.
    ADD CONSTRAINT chk_climbing_ascents_grade_scale CHECK (
        (discipline = 'BOULDER'          AND starts_with(grade, 'FB_'))
     OR (discipline IN ('SPORT', 'TRAD') AND starts_with(grade, 'FR_'))
     OR (terrain = 'MOUNTAIN'            AND starts_with(grade, 'FR_'))
    ),

    -- Poprowadzone trudności są na tej samej osi co sama droga.
    ADD CONSTRAINT chk_climbing_ascents_led_scale
        CHECK (led_grade IS NULL OR starts_with(led_grade, 'FR_')),

    -- Pinkpoint i toprope to pojęcia linowe — w boulderze nie ma czego wpiąć ani na czym wisieć.
    ADD CONSTRAINT chk_climbing_ascents_boulder_style
        CHECK (discipline IS NULL OR discipline <> 'BOULDER' OR style IN ('OS', 'FLASH', 'RP')),

    -- Granice łapiące zgubioną cyfrę, nie ograniczające ambicji: 4000 m ściany nie ma nigdzie,
    -- 60 wyciągów to już wielka ściana, 30 dni to więcej niż najdłuższe przejścia w historii.
    ADD CONSTRAINT chk_climbing_ascents_length
        CHECK (length_meters IS NULL OR length_meters BETWEEN 1 AND 4000),
    ADD CONSTRAINT chk_climbing_ascents_pitches
        CHECK (pitches IS NULL OR pitches BETWEEN 1 AND 60),
    ADD CONSTRAINT chk_climbing_ascents_duration
        CHECK (duration_minutes IS NULL OR duration_minutes BETWEEN 1 AND 43200),
    -- Nie da się poprowadzić więcej wyciągów, niż droga ma — ani ich policzyć bez podania,
    -- ile ma cała droga.
    ADD CONSTRAINT chk_climbing_ascents_led_pitches CHECK (
        led_pitches IS NULL OR (pitches IS NOT NULL AND led_pitches BETWEEN 0 AND pitches)
    );

-- Lista i statystyki zawsze pytają o jeden teren naraz.
CREATE INDEX idx_climbing_ascents_athlete_terrain
    ON climbing_ascents (athlete_id, terrain, climbed_on DESC);

COMMENT ON COLUMN climbing_ascents.terrain IS
    'ROCK = przejście skalne (ma dyscyplinę, próby, gwiazdki), MOUNTAIN = górskie (ma sezon, '
    'długość, wyciągi, czas, partnerów i poprowadzone trudności). Kształt wymuszają CHECK-i.';

COMMENT ON COLUMN climbing_ascents.original_grade IS
    'Wycena w skali oryginalnej jako CYTAT z przewodnika (V, UIAA VI, WI4). Wolny tekst — '
    'statystyki liczą wyłącznie po grade (skala ujednolicona), więc to pole nic nie sortuje.';
