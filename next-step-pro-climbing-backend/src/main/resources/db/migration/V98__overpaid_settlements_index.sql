-- Lustro idx_settlements_underpaid z V96.
--
-- Zakładka Rozliczenia zadaje w jednym żądaniu oba pytania o resztę: „komu zostało do dopłacenia"
-- (paid < amount, indeks z V96) i „czyje pieniądze u siebie trzymam" (paid > amount, ten indeks).
-- Do tej pory istniała tylko pierwsza połowa, bo drugiej nikt nie pytał — nadpłatę liczył SUM po
-- wierszach dłużników. Odkąd jest lista osób z samą nadpłatą, predykat jest odczytem pierwszej
-- klasy i zasługuje na ten sam plan co jego odwrotność; bez tego dwie połowy jednej funkcji
-- rozjeżdżają się wraz z tabelą, a objawem jest wolniejsze /overview, nie błąd.
--
-- Kolumna w indeksie jest ta sama co w V96 (user_id), bo to po niej grupuje się wynik. Goście
-- świadomie bez własnego indeksu: uq_settlements_guest daje jeden wiersz na gościa, więc ich
-- podzbiór jest z definicji mały.
CREATE INDEX idx_settlements_overpaid ON settlements (user_id) WHERE paid_amount > amount;

COMMENT ON INDEX idx_settlements_overpaid IS
    'Wiersze trzymajace nadplate — zasilaja karte "Nadplaty" na zakladce Rozliczen ORAZ adnotacje '
    'nadplaty przy dlugu. Jeden odczyt, dwa ekrany, wiec obie liczby nie moga sie roznic.';
