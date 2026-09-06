-- Rozmowa zawodnik <-> trener również pod terminem wziętym z kalendarza ogólnego.
--
-- Do tej pory wątek dało się prowadzić wyłącznie pod wpisem z planu 1:1, bo training_id było
-- NOT NULL. Skutek był taki, że o możliwości rozmowy decydowało NARZĘDZIE, którym trener wpisał
-- termin — a tego zawodnik nie widzi: ten sam trening wpisany z panelu 1:1 miał wątek, a wzięty
-- z kalendarza ogólnego był niemy. Rozmowa i tak była już otwarta w połowie, bo zawodnik ocenia
-- odbytą rezerwację RPE RAZEM Z NOTATKĄ (reservation_rpe.note, 500 znaków pseudo-markdownu),
-- czyli pisał wiadomość, na którą nie było gdzie odpowiedzieć.
--
-- Poszerzenie tej tabeli, a NIE nowa tabela obok: wątek to jeden komponent frontu, jeden zestaw
-- załączników (training_comment_files wisi na komentarzu), jedna retencja 12 miesięcy i jeden
-- endpoint edycji. Bliźniacza tabela byłaby trzecią odsłoną duplikacji slot/event, która w tym
-- repo już kosztowała bugi — a jej wzorzec awarii jest zawsze ten sam: poprawka trafia do jednej
-- kopii.

-- ---------------------------------------------------------------------------
-- 1. Właściciel kalendarza, jawnie, na KAŻDYM wierszu
-- ---------------------------------------------------------------------------
-- Dziś pięć zapytań o nieprzeczytane dochodzi do zawodnika przez join c.training.athlete.id.
-- Wiersz bez treningu nie ma tej drogi, więc bez tej kolumny każde z nich musiałoby dostać
-- gałąź "albo trening, albo termin" — a niedokończona robota daje tu najgorszy możliwy bug:
-- wiadomość, która nigdy nie dzwoni, i o której nikt się nie dowie, bo nic nie pada.
-- Z kolumną te zapytania tracą join i nie mają czego rozgałęziać.
--
-- Nullowalna tylko na czas backfillu — po nim NOT NULL, bo "komentarz bez kalendarza" nie jest
-- stanem, w którym cokolwiek w tej aplikacji umie ten wiersz pokazać.
ALTER TABLE training_comments ADD COLUMN athlete_id UUID REFERENCES users(id) ON DELETE CASCADE;

UPDATE training_comments c
   SET athlete_id = t.athlete_id
  FROM personal_trainings t
 WHERE t.id = c.training_id;

ALTER TABLE training_comments ALTER COLUMN athlete_id SET NOT NULL;

-- ---------------------------------------------------------------------------
-- 2. Cel: trening ALBO slot ALBO wydarzenie
-- ---------------------------------------------------------------------------
-- Trzy prawdziwe FK, nie para (target_type, target_id) — ten sam wybór i ten sam powód co
-- w admin_private_notes: ON DELETE CASCADE jest MECHANIZMEM. Para z dyskryminatorem zostawiałaby
-- po skasowanym slocie wiersze z cudzą rozmową, których nic nie sprząta i których nikt już nie
-- zobaczy, żeby je usunąć.
--
-- ⚠️ Adresem rozmowy jest PARA (cel, zawodnik), nie sam cel: na jednym slocie potrafią stać dwie
-- osoby, a wątek ma być prywatny. Stąd athlete_id w obu indeksach niżej.
--
-- ⚠️ Celem NIE jest rezerwacja — dokładnie ta sama reguła co przy rozliczeniach. Zapis na
-- wielodniowe wydarzenie zakłada JEDEN WIERSZ reservations NA DZIEŃ, więc wątek na rezerwacji
-- rozbiłby trzydniowy kurs na trzy osobne rozmowy, a anulowanie i ponowny zapis (nowy wiersz)
-- gubiłoby całą historię. Rezerwacja jest tylko ADRESEM NA DRUCIE: serwis rozwiązuje ją do pary
-- (cel, zawodnik), bo jako jedyna niesie obie połówki naraz.
ALTER TABLE training_comments ALTER COLUMN training_id DROP NOT NULL;
ALTER TABLE training_comments ADD COLUMN time_slot_id UUID REFERENCES time_slots(id) ON DELETE CASCADE;
ALTER TABLE training_comments ADD COLUMN event_id     UUID REFERENCES events(id)     ON DELETE CASCADE;

-- Wydarzenie niesie JEDNĄ rozmowę niezależnie od tego, ile dni trwa, więc wątek kursu wisi na
-- evencie, nigdy na dobowych slotach, które zakłada mu pierwszy zapis rezerwacji (serwis odrzuca
-- slot z event_id — tu widać tylko, że kolumny są rozłączne).
ALTER TABLE training_comments ADD CONSTRAINT chk_training_comments_single_target CHECK (
    (training_id  IS NOT NULL)::int
  + (time_slot_id IS NOT NULL)::int
  + (event_id     IS NOT NULL)::int = 1);

-- ---------------------------------------------------------------------------
-- 3. Indeksy
-- ---------------------------------------------------------------------------
-- Wątek czytany jest po parze (cel, zawodnik); liczniki nieprzeczytanych po samym zawodniku.
-- Partial, bo w wierszu dwie z trzech kolumn celu są NULL i bez predykatu indeks nosiłby połowę
-- tabeli, której nigdy nie przeszuka.
CREATE INDEX idx_training_comments_athlete ON training_comments (athlete_id);
CREATE INDEX idx_training_comments_slot
    ON training_comments (time_slot_id, athlete_id) WHERE time_slot_id IS NOT NULL;
CREATE INDEX idx_training_comments_event
    ON training_comments (event_id, athlete_id)     WHERE event_id     IS NOT NULL;
