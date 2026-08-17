-- Trad dostaje WŁASNY słownik stylów: OS_GU, FLASH_GU, GU, HP. Nic z nim nie dzieli.
--
-- Powód jest domenowy, nie kosmetyczny: na własnej asekuracji pytaniem nie jest "ile wiedziałeś",
-- tylko "skąd to robiłeś". Sportowe OS/FLASH/RP tego nie mówią — tradowy onsight jest od dołu
-- z definicji, a gołe "RP" ukrywa, czy droga była opracowana z góry na wędce. Stąd GU (ground up:
-- każda próba od dołu, przeloty zakładane w prowadzeniu) i HP (headpoint: poprowadzone po
-- opracowaniu z liny z góry). TR i solo wypadają z tradu, bo tradowy dziennik jest zapisem
-- prowadzenia; AF nigdy w tym systemie nie było.
--
-- Rodzina GU/HP jest WYŁĄCZNIE tradowa — w sporcie i bulderze nie ma czego zakładać, a droga
-- alpejska jest od dołu domyślnie, więc etykieta opisywałaby przypadek normalny (dokładnie ten
-- sam argument, co przy braku FREE_SOLO w bulderze). Pilnuje tego chk_climbing_ascents_trad_style
-- w OBIE strony: trad musi używać nowych stylów, a reszta nie może.
--
-- Konwersja przed zmianą CHECK-a. OS i FLASH w tradzie są od dołu z definicji, więc mapują się
-- wprost. RP → HP, a nie → GU: z "RP" nie widać, czy droga była robiona od dołu, a podniesienie
-- komuś stylu za niego byłoby dopisaniem przejścia, którego nie zgłosił. Wpisy tradowe z wędką
-- i solo trafiają do dyscypliny SPORT zamiast zniknąć — styl zostaje prawdziwy, a wiersz nie
-- ginie po cichu; obie skale są francuskie, więc wycena pasuje bez tłumaczenia.
-- ⚠️ CHECK-i lecą PRZED konwersją, nie po niej. V85 mogła przepisywać dane najpierw, bo PP→RP
-- to ruch WEWNĄTRZ starej listy; tutaj docelowe wartości jeszcze na niej nie stoją, więc pierwszy
-- UPDATE wywala się na chk_climbing_ascents_style i migracja nie wstaje w ogóle.
ALTER TABLE climbing_ascents DROP CONSTRAINT chk_climbing_ascents_style;
ALTER TABLE climbing_ascents DROP CONSTRAINT chk_climbing_ascents_first_try;

UPDATE climbing_ascents SET discipline = 'SPORT'
    WHERE discipline = 'TRAD' AND style IN ('TR', 'SOLO', 'FREE_SOLO');

UPDATE climbing_ascents SET style = 'OS_GU'    WHERE discipline = 'TRAD' AND style = 'OS';
UPDATE climbing_ascents SET style = 'FLASH_GU' WHERE discipline = 'TRAD' AND style = 'FLASH';
UPDATE climbing_ascents SET style = 'HP'       WHERE discipline = 'TRAD' AND style = 'RP';

ALTER TABLE climbing_ascents
    ADD CONSTRAINT chk_climbing_ascents_style CHECK (style IN (
        'OS', 'FLASH', 'RP', 'TR', 'SOLO', 'FREE_SOLO', 'A0',
        'OS_GU', 'FLASH_GU', 'GU', 'HP'
    )),

    -- Trad ⇔ dialekt tradowy. CASE, a nie równość dwóch warunków: dla gór discipline jest NULL,
    -- więc `discipline = 'TRAD'` daje NULL i porównanie przepuściłoby wszystko — a wpis górski
    -- ma tych stylów nie mieć. Góry i tak łapie chk_climbing_ascents_mountain_style; ten CHECK
    -- jest drugim zamkiem, bo lista stylów bez właściciela rozjeżdża się po cichu.
    ADD CONSTRAINT chk_climbing_ascents_trad_style CHECK (
        CASE WHEN discipline = 'TRAD'
             THEN style IN ('OS_GU', 'FLASH_GU', 'GU', 'HP')
             ELSE style NOT IN ('OS_GU', 'FLASH_GU', 'GU', 'HP')
        END
    ),

    -- OS i FLASH ZNACZĄ "za pierwszym razem", w każdym dialekcie — tradowe OS GU i Flash GU
    -- tak samo. Serwis normalizuje do 1; ten CHECK jest zapasem na drogę pomijającą serwis.
    ADD CONSTRAINT chk_climbing_ascents_first_try CHECK (
        attempts IS NULL
        OR style NOT IN ('OS', 'FLASH', 'OS_GU', 'FLASH_GU')
        OR attempts = 1
    );
