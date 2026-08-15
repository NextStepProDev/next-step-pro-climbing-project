-- Czy przejścia użytkownika trafiają na publiczną listę "Ostatnie przejścia" na stronie
-- Aktualności (imię, nazwisko, droga, wycena, styl, rejon, data).
--
-- DEFAULT TRUE, czyli opt-OUT, a nie opt-in. To świadoma decyzja właściciela serwisu: lista ma
-- żyć od pierwszego dnia, a nie czekać, aż ktoś ją odkryje w ustawieniach. Cena jest realna i
-- dlatego kolumna w ogóle istnieje — publikacja imienia i nazwiska to przetwarzanie danych
-- osobowych, więc RODO wymaga skutecznego sprzeciwu (art. 21). Ta kolumna JEST tym sprzeciwem:
-- jedno przełączenie w ustawieniach zdejmuje z listy wszystkie wpisy naraz, bez proszenia
-- administratora o grzebanie w bazie.
--
-- Wpisy nie mają własnej flagi i to też jest decyzją: "ukryj to jedno przejście" brzmi
-- rozsądnie, dopóki nie trzeba wytłumaczyć, czemu statystyki liczą wpis, którego nie widać.
-- Widoczność jest własnością AUTORA, nie pojedynczego wiersza.
ALTER TABLE users ADD COLUMN ascents_public BOOLEAN NOT NULL DEFAULT true;

COMMENT ON COLUMN users.ascents_public IS
    'Czy przejścia użytkownika pokazują się na publicznej liście w Aktualnościach. '
    'Opt-out (domyślnie true) — mechanizm sprzeciwu z RODO art. 21.';
