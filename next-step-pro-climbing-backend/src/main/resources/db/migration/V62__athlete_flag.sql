-- Flaga "podopieczny trenera" (zawodnik). Ustawiana ręcznie przez admina w panelu.
-- Tylko oznaczeni użytkownicy widzą osobisty kalendarz treningowy w "Moje rezerwacje"
-- i mogą korzystać z /api/training-calendar. Odznaczenie NIE kasuje danych kalendarza.
ALTER TABLE users ADD COLUMN is_athlete BOOLEAN NOT NULL DEFAULT false;
