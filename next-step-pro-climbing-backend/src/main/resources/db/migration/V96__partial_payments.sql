-- Ile klient FAKTYCZNIE dał, obok tego, ile był winien.
--
-- Do tej pory rozliczenie było zero-jedynkowe: settled_on puste = winien wszystko, ustawione =
-- zapłacone w całości. Nie dało się zapisać ani „dał 200 za 150, bo nie miał drobnych", ani „dał
-- 100 ze 150" — a jedno i drugie zdarza się przy gotówce stale.
--
-- ⚠️ Kluczowe: SALDO NIE JEST OSOBNYM POLEM. Wylicza się jako suma otrzymanego minus suma należnego
-- po wszystkich wierszach danej osoby. Kolumna „nadpłata" trzymana obok byłaby drugim źródłem
-- prawdy, które trzeba pamiętać, żeby aktualizować przy każdej korekcie kwoty — czyli takim, które
-- rozjeżdża się po pierwszej poprawce i nie mówi o tym.
ALTER TABLE settlements ADD COLUMN paid_amount NUMERIC(10,2) NOT NULL DEFAULT 0;

-- Migracja danych jest jednoznaczna i nie wymyśla żadnych sald: co było odhaczone jako zapłacone,
-- było zapłacone w całości; reszta ma zero.
UPDATE settlements SET paid_amount = amount WHERE settled_on IS NOT NULL;

-- Górny limit ten sam co przy kwocie. Nadpłata JEST dozwolona (paid_amount > amount) i to jest cała
-- druga połowa tej funkcji — brak takiego wiersza znaczyłby, że reszta z dwustu przepada.
ALTER TABLE settlements ADD CONSTRAINT chk_settlements_paid_range CHECK (
    paid_amount >= 0 AND paid_amount <= 100000);

-- Lista „do odzyskania" pyta teraz o RESZTĘ, nie o brak daty: wiersz zapłacony w 100 ze 150 dalej
-- wisi, tylko na 50. Poprzedni indeks (settled_on IS NULL) odpowiadał na nieaktualne pytanie.
DROP INDEX IF EXISTS idx_settlements_unsettled;
CREATE INDEX idx_settlements_underpaid ON settlements (user_id) WHERE paid_amount < amount;

COMMENT ON COLUMN settlements.paid_amount IS
    'Ile faktycznie wplynelo na ten wiersz. Mniej niz amount = reszta nadal nalezna; wiecej = '
    'nadplata, ktora podnosi saldo klienta. Saldo = SUM(paid_amount) - SUM(amount) po jego wierszach '
    'i nigdzie nie jest przechowywane.';

COMMENT ON COLUMN settlements.settled_on IS
    'Dzien, w ktorym ostatnio wplynely pieniadze na ten wiersz. NULL = nie wplynelo nic. Sam w sobie '
    'NIE znaczy juz "zaplacone w calosci" — od tego jest porownanie paid_amount z amount.';
