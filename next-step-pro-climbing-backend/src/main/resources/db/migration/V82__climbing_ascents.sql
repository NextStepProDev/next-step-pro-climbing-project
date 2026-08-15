-- Dziennik przejść wspinaczkowych zawodnika. Wpis = PRZEJŚCIE UKOŃCZONE; prób ani projektów
-- tu nie ma i to jest decyzja, nie brak. Piramida trudności i onsight-rate liczą przejścia,
-- więc wpis "próbowałem" wpadłby do mianownika obu i zamienił dziennik osiągnięć w dziennik
-- obecności — a od "czy się dziś ruszałem" jest kalendarz treningowy.
--
-- Wpisuje WYŁĄCZNIE zawodnik. Trener czyta i eksportuje, ale nie ma endpointu zapisu —
-- dokładnie jak przy wadze (V74): zaliczenie komuś przejścia nie jest decyzją trenera.
--
-- WYCENA JEST Z ZAMKNIĘTEJ LISTY (enum ClimbingGrade), nie wolnym tekstem. Ranking po wolnym
-- tekście już raz w tym projekcie padł — kafelek "najczęstsze miejsca" czytał events.location
-- jako klucz grupowania i "Jura Północna" vs "Jura Północna / Kołoczek" wychodziły jako dwa
-- miejsca. Tam ceną była myląca lista; tutaj byłaby wyższa, bo "7a" i "7A" różnią się o trzy
-- stopnie trudności i wpadłyby do jednego słupka.
--
-- Kolumna grade trzyma NAZWĘ STAŁEJ ENUMU (FR_7A_PLUS / FB_7A_PLUS), nie etykietę ("7a+"/"7A+").
-- Etykiety obu skal kolidują po zignorowaniu wielkości liter, więc jedno nieopatrzne lower()
-- w zapytaniu scaliłoby skalę drogową z bulderową. Prefiks FR_/FB_ jest nośny: pilnuje go
-- chk_climbing_ascents_grade_scale, więc przemianowanie stałych bez migracji danych rozjeżdża
-- CHECK z aplikacją.
--
-- RANGI wyceny NIE MA w bazie, świadomie. Ranga jest własnością listy, nie wiersza: zapisana
-- w kolumnie wymagałaby migracji danych przy wsunięciu 9c+, a nikt nie sortuje po niej w SQL —
-- statystyki liczy jeden przebieg w Javie (wzorzec TrainingStatsService), lista sortuje się
-- na kliencie. Ten sam podział co przy wadze: baza trzyma surowe, aplikacja liczy pochodne.
CREATE TABLE climbing_ascents (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    athlete_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    climbed_on    DATE NOT NULL,
    discipline    VARCHAR(20)  NOT NULL,
    grade         VARCHAR(20)  NOT NULL,
    style         VARCHAR(10)  NOT NULL,
    area          VARCHAR(120) NOT NULL,
    crag          VARCHAR(120) NOT NULL,
    route_name    VARCHAR(160) NOT NULL,
    -- Klucze grupujące: lowercase + bez diakrytyków + zwinięte spacje, liczone w APLIKACJI
    -- (AscentTextKey, czysta klasa z testem). Nie kolumna generowana: unaccent() jest STABLE,
    -- nie IMMUTABLE, więc w GENERATED ALWAYS AS nie przejdzie — a reguła normalizacji ma i tak
    -- mieć jedno miejsce, zamiast żyć równolegle w Javie i w dialekcie SQL.
    area_key      VARCHAR(120) NOT NULL,
    crag_key      VARCHAR(120) NOT NULL,
    -- Ile prób/dni pracy zajęło przejście, LICZĄC TĘ UDANĄ. Jedna kolumna, nie dwie: "próby"
    -- i "dni pracy" to ta sama liczba mierzona w innej rozdzielczości, a dwie kolumny kazałyby
    -- przy każdym liczeniu pytać, którą teraz brać.
    attempts      INTEGER,
    quality_stars SMALLINT,
    comment       TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_climbing_ascents_discipline
        CHECK (discipline IN ('SPORT', 'BOULDER', 'TRAD')),
    CONSTRAINT chk_climbing_ascents_style
        CHECK (style IN ('OS', 'FLASH', 'RP', 'PP', 'TR')),
    -- Oś wyceny wynika z dyscypliny i baza ma tego pilnować, nie tylko serwis: wiersz z bulderem
    -- na skali drogowej przewróciłby piramidę CICHO, bo obie wartości są poprawnymi wycenami
    -- i nic w danych nie wygląda na uszkodzone. Prefiks zamiast wyliczenia 54 wartości —
    -- dopisanie 9c+ ma nie wymagać migracji.
    CONSTRAINT chk_climbing_ascents_grade_scale CHECK (
        (discipline = 'BOULDER'          AND starts_with(grade, 'FB_'))
     OR (discipline IN ('SPORT', 'TRAD') AND starts_with(grade, 'FR_'))
    ),
    -- Pinkpoint i toprope to pojęcia linowe. W boulderze nie ma czego wpiąć ani na czym wisieć.
    CONSTRAINT chk_climbing_ascents_boulder_style
        CHECK (discipline <> 'BOULDER' OR style IN ('OS', 'FLASH', 'RP')),
    -- Wpis jest przejściem, więc prób jest co najmniej jedna. Górna granica łapie zgubioną cyfrę.
    CONSTRAINT chk_climbing_ascents_attempts
        CHECK (attempts IS NULL OR attempts BETWEEN 1 AND 9999),
    -- OS i FLASH ZNACZĄ "za pierwszym razem". To definicja, nie preferencja, więc "onsight
    -- w czterech próbach" nie jest wpisem do przyjęcia i zaokrąglenia — jest wpisem sprzecznym
    -- z samym sobą. Serwis normalizuje do 1; ten CHECK jest zapasem na drogę pomijającą serwis.
    CONSTRAINT chk_climbing_ascents_first_try
        CHECK (attempts IS NULL OR style NOT IN ('OS', 'FLASH') OR attempts = 1),
    CONSTRAINT chk_climbing_ascents_stars
        CHECK (quality_stars IS NULL OR quality_stars BETWEEN 0 AND 5),
    CONSTRAINT chk_climbing_ascents_comment_len
        CHECK (comment IS NULL OR length(comment) <= 2000),
    CONSTRAINT chk_climbing_ascents_keys_present
        CHECK (length(area_key) > 0 AND length(crag_key) > 0 AND length(route_name) > 0)
);

-- Każdy odczyt jest per zawodnik i albo po roku, albo po całości, zawsze najnowsze na górze.
CREATE INDEX idx_climbing_ascents_athlete_date
    ON climbing_ascents (athlete_id, climbed_on DESC);

-- Podpowiedzi rejonu/skały w formularzu i ranking rejonów grupują po kluczu, nie po napisie.
CREATE INDEX idx_climbing_ascents_athlete_area
    ON climbing_ascents (athlete_id, area_key);

-- ŚWIADOMIE BEZ UNIQUE. Powtórka drogi jest normalnym wpisem (7a z 2019 i z 2026 to dwa
-- przejścia), a nawet dwa wpisy tej samej drogi tego samego dnia bywają prawdą (rozgrzewka
-- na wędkę, potem RP). Unikat wyglądałby na higienę, a byłby regułą domenową, której w tej
-- domenie nie ma — i odbijałby zawodnikowi wpisy, które są poprawne.

COMMENT ON COLUMN climbing_ascents.grade IS
    'Nazwa stałej enumu ClimbingGrade (FR_* dla skali francuskiej drogowej, FB_* dla Fontainebleau). '
    'Prefiks jest nośny — pilnuje go chk_climbing_ascents_grade_scale, więc przemianowanie stałych '
    'bez migracji danych rozjeżdża CHECK z aplikacją.';

COMMENT ON COLUMN climbing_ascents.area_key IS
    'Znormalizowany klucz grupowania rejonu (AscentTextKey.normalize): lowercase, bez diakrytyków, '
    'zwinięte spacje. Do rankingów i podpowiedzi; do wyświetlania służy kolumna area.';
