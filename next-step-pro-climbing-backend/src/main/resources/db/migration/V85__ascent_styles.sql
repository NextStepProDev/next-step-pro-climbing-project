-- Style przejść: dochodzą SOLO i FREE_SOLO, znika PP, a w górach dodatkowo TR.
--
-- PP (pinkpoint) wypada, bo to rozróżnienie, którego prawie nikt nie zapisuje, a każdy styl
-- w liście rozwijanej jest pytaniem, na które wspinacz musi odpowiedzieć. SOLO (samotnie, ale
-- z liną) i FREE_SOLO (bez liny, po polsku "na żywca") wchodzą, bo opisują coś, czego żaden
-- z pozostałych stylów nie umie powiedzieć.
--
-- TR znika Z GÓR, nie z całości: na skałce wędka to normalne przejście, a w drodze alpejskiej
-- nie ma z czego jej zawiesić — oferowanie jej tam byłoby pytaniem bez uczciwej odpowiedzi.
-- FREE_SOLO nie wchodzi do bulderu: każdy blok robi się bez liny, więc etykieta opisywałaby
-- przypadek normalny.
--
-- Konwersja przed zmianą CHECK-a: wpisy PP stają się RP (redpoint to najbliższe prawdzie —
-- droga poprowadzona po pracy). Na produkcji ta tabela jest jeszcze pusta, ale migracja musi
-- działać także tam, gdzie ktoś zdążył coś wpisać lokalnie.
UPDATE climbing_ascents SET style = 'RP' WHERE style = 'PP';
UPDATE climbing_ascents SET style = 'RP' WHERE terrain = 'MOUNTAIN' AND style = 'TR';

ALTER TABLE climbing_ascents DROP CONSTRAINT chk_climbing_ascents_style;
ALTER TABLE climbing_ascents DROP CONSTRAINT chk_climbing_ascents_boulder_style;

ALTER TABLE climbing_ascents
    ADD CONSTRAINT chk_climbing_ascents_style
        CHECK (style IN ('OS', 'FLASH', 'RP', 'TR', 'SOLO', 'FREE_SOLO')),

    -- Boulder: nie ma na czym wisieć, a "bez liny" to w bulderze stan domyślny.
    ADD CONSTRAINT chk_climbing_ascents_boulder_style CHECK (
        discipline IS NULL OR discipline <> 'BOULDER'
        OR style IN ('OS', 'FLASH', 'RP')
    ),

    -- Góry: bez wędki.
    ADD CONSTRAINT chk_climbing_ascents_mountain_style CHECK (
        terrain <> 'MOUNTAIN' OR style IN ('OS', 'FLASH', 'RP', 'SOLO', 'FREE_SOLO')
    );
