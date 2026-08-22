-- Zdjęcie POJEDYNCZEGO wpisu z publicznej listy "Ostatnie przejścia" przez właściciela serwisu.
--
-- Uwaga, bo to wygląda na zaprzeczenie V83: tamta migracja zapisała, że "wpisy nie mają własnej
-- flagi, widoczność jest własnością AUTORA". To nadal obowiązuje i ta kolumna tego nie łamie,
-- bo odpowiada na inne pytanie i należy do kogo innego:
--
--   users.ascents_public          -- ŻYCZENIE AUTORA: czy w ogóle chcę być na tej liście.
--   climbing_ascents.hidden_...   -- DECYZJA OPERATORA: ten jeden wpis nie ma wisieć na stronie.
--
-- To dwa różne czasowniki ("rezygnuję" vs "zdejmuję"), które dają się zapisać w tym samym polu
-- tylko pozornie. Zapisanie ich razem — czyli pozwolenie adminowi przestawiać cudze
-- ascents_public — kosztowałoby dwa razy: (1) ta flaga bramkuje też podgląd dziennika w panelu,
-- więc admin zdejmujący kogoś z listy oślepiłby własny widok tej osoby; (2) autor widzi swój
-- przełącznik w Ustawieniach, więc cofnąłby zdjęcie jednym kliknięciem. Moderacja, którą
-- moderowany cofa, nie jest moderacją.
--
-- Zabrania autorowi TYLKO publikacji tego wiersza. Wpis zostaje w jego dzienniku, liczy się w
-- jego statystykach i w piramidzie — bo to nadal jego przejście. Zniknął z cudzej tablicy
-- ogłoszeń, a nie z własnego zeszytu.
--
-- Znacznik czasu, nie boolean: dom tego repozytorium (completed_at, achieved_at, notified_at,
-- expires_at). NULL = wisi normalnie. Przy okazji zostaje ślad, kiedy to się stało — za darmo,
-- bo i tak trzeba było czymś oznaczyć.
ALTER TABLE climbing_ascents ADD COLUMN hidden_from_public_at TIMESTAMPTZ;

COMMENT ON COLUMN climbing_ascents.hidden_from_public_at IS
    'Kiedy właściciel serwisu zdjął ten wpis z publicznej listy. NULL = wpis jest publikowany '
    'normalnie (o ile autor nie wyłączył users.ascents_public). Autor nie ma na to kontrolki — '
    'przywrócić może tylko admin.';

-- Zapytanie publicznej listy filtruje po tej kolumnie ORAZ po users.ascents_public. Indeks jest
-- częściowy, bo zdjęte wpisy będą rzadkością, a wszystkie pozostałe i tak muszą przejść przez
-- sortowanie po climbed_on.
CREATE INDEX idx_climbing_ascents_public_feed
    ON climbing_ascents (climbed_on DESC, created_at DESC)
    WHERE hidden_from_public_at IS NULL;
