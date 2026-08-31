-- Rozliczenia: ile kosztował termin KONKRETNĄ osobę i czy zapłaciła. Widzi to wyłącznie admin.
--
-- Kwota wisi na UCZESTNIKU, nie na terminie, i to jest cała decyzja tej tabeli. Jeden checkbox na
-- slocie z dwiema osobami nie umie powiedzieć, która zapłaciła, a statystyka "per użytkownik"
-- musiałaby wtedy dzielić kwotę po równo albo przypisywać całość każdemu — obie odpowiedzi są
-- nieprawdziwe. Osobny wiersz na osobę pozwala też dać komuś zniżkę albo doliczyć sprzęt.
--
-- ⚠️ CEL TO (slot XOR wydarzenie), NIGDY reservation_id — i to jest najważniejszy szczegół.
-- Zapis na wielodniowe wydarzenie zakłada JEDEN WIERSZ reservations NA DZIEŃ (ReservationService
-- .createEventReservation tworzy po slocie na dobę i rezerwuje wszystkie). Rozliczenie wiszące na
-- rezerwacji dałoby więc przy 3-dniowym kursie trzy pola po 600 zł i policzyło 1800 zł przychodu.
-- Para (termin, płatnik) daje jedno rozliczenie na osobę Z KONSTRUKCJI, a nie przez odsiewanie
-- duplikatów przy odczycie, którego ktoś kiedyś zapomni nałożyć. Ten kształt jest już w repo:
-- reserved_seats (time_slot_id XOR event_id + user_id).
--
-- Konsekwencja, którą trzeba znać: dobowe sloty wydarzenia są księgowością zakładaną przez pierwszą
-- rezerwację i admin ich nie widzi, więc rozliczenie na slocie z event_id jest odrzucane przez
-- serwis (400) — dokładnie jak prywatna notatka. Tu widać tylko, że kolumny celu są rozłączne.
--
-- Dwa CHECK-i zamiast jednego czterokolumnowego: to dwa niezależne pytania — O KTÓRY TERMIN chodzi
-- i KTO PŁACI. Sklejone w jeden warunek dałyby komunikat naruszenia, z którego nie wynika, która
-- połowa jest zła.
--
-- Goście liczą się do przychodu (decyzja właściciela), a GuestReservation jest już sam w sobie
-- "jeden wiersz na zgłoszenie" — także dla wydarzenia wielodniowego — więc wystarczy za płatnika.
-- Gość nie ma konta, więc w statystyce per osoba pojawia się pod wpisaną nazwą, bez linku do karty.
CREATE TABLE settlements (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    time_slot_id         UUID REFERENCES time_slots(id)         ON DELETE CASCADE,
    event_id             UUID REFERENCES events(id)             ON DELETE CASCADE,
    user_id              UUID REFERENCES users(id)              ON DELETE CASCADE,
    guest_reservation_id UUID REFERENCES guest_reservations(id) ON DELETE CASCADE,

    -- NUMERIC, nie float: pieniądze. Górny limit łapie zgubione zero tak samo jak CHECK 20–300 kg
    -- przy wadze; zero jest DOZWOLONE, bo "gratis" to decyzja, którą trzeba dać się zapisać.
    amount               NUMERIC(10,2) NOT NULL,

    -- NULL = nierozliczone. Znacznik zamiast boolean + osobna data: dom tego repozytorium
    -- (completed_at, achieved_at, notified_at, hidden_from_public_at).
    --
    -- DATE, nie TIMESTAMPTZ, bo to etykieta dnia w Polsce, a nie moment — jak training_date i
    -- measured_on. Podpowiadana jest DATA TERMINU, nie dzień kliknięcia: dzięki temu wpłata za
    -- marcowe zajęcia odhaczona w kwietniu domyślnie ląduje w marcu, a admin może ją nadpisać,
    -- gdy pieniądze naprawdę przyszły kiedy indziej.
    settled_on           DATE,

    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_settlements_single_target CHECK (
        (time_slot_id IS NOT NULL)::int
      + (event_id     IS NOT NULL)::int = 1),

    CONSTRAINT chk_settlements_single_payer CHECK (
        (user_id              IS NOT NULL)::int
      + (guest_reservation_id IS NOT NULL)::int = 1),

    CONSTRAINT chk_settlements_amount_range CHECK (amount >= 0 AND amount <= 100000)
);

-- Jedno rozliczenie na parę (termin, płatnik). Indeksy są PARTIAL, bo w każdym wierszu połowa
-- kolumn celu i płatnika jest NULL, a NULL-e nie kolidują ze sobą w zwykłym UNIQUE — bez predykatu
-- indeks przepuściłby dowolnie wiele rozliczeń "bez slotu". Predykat jest też warunkiem, żeby
-- ON CONFLICT (...) WHERE ... mógł ten indeks wskazać (ta sama pułapka co w V89).
CREATE UNIQUE INDEX uq_settlements_slot_user
    ON settlements (time_slot_id, user_id) WHERE time_slot_id IS NOT NULL AND user_id IS NOT NULL;
CREATE UNIQUE INDEX uq_settlements_event_user
    ON settlements (event_id, user_id)     WHERE event_id     IS NOT NULL AND user_id IS NOT NULL;
CREATE UNIQUE INDEX uq_settlements_guest
    ON settlements (guest_reservation_id)  WHERE guest_reservation_id IS NOT NULL;

-- Lista "do odzyskania" jest jedynym odczytem, który celowo IGNORUJE filtr roku — dług sprzed
-- dwóch lat nadal jest długiem — więc jako jedyny czyta całą historię i dostaje własny indeks.
-- Przychód idzie po settled_on w obrębie roku i mieści się w seq scanie tej tabeli.
CREATE INDEX idx_settlements_unsettled ON settlements (settled_on) WHERE settled_on IS NULL;

-- ⚠️ user_id jako JEDYNA kolumna celu/płatnika potrzebuje własnego indeksu, i to z dwóch powodów.
-- (1) Postgres NIE indeksuje kolumn FK automatycznie, a ON DELETE CASCADE przy kasowaniu konta
-- musi znaleźć wiersze potomne — bez indeksu każde usunięcie użytkownika to seq scan rozliczeń,
-- doklejony do i tak długiej sekwencji z UserService.deleteAccount. Pozostałe trzy FK mają swoje
-- kolumny na PIERWSZEJ pozycji indeksów unikalnych powyżej, więc są pokryte; user_id stoi tam
-- dopiero na drugiej i nie da się po nim wejść.
-- (2) Podpowiedź kwoty (SettlementRepository.findLastAmountsForUsers) grupuje po user_id ze
-- skorelowanym MAX — a to jedyne zapytanie sekcji czytające całą historię, odpalane przy każdym
-- otwarciu slotu przez admina.
CREATE INDEX idx_settlements_user ON settlements (user_id) WHERE user_id IS NOT NULL;

COMMENT ON TABLE settlements IS
    'Kwota i status zapłaty dla pary (termin, płatnik). Termin to slot ALBO wydarzenie — nigdy '
    'rezerwacja, bo wielodniowe wydarzenie ma jeden wiersz rezerwacji na dzień. Widoczne wyłącznie '
    'dla admina; typy tej funkcji są zamknięte w domain/settlement i api/admin/settlement, czego '
    'pilnuje SettlementIsolationTest.';

COMMENT ON COLUMN settlements.settled_on IS
    'Data zapłaty (etykieta dnia PL). NULL = nierozliczone. Podpowiadana jako data terminu, '
    'edytowalna ręcznie.';

COMMENT ON COLUMN settlements.amount IS
    'Kwota w PLN za CAŁY wiersz rezerwacji, nie za głowę — rezerwacja może obejmować kilka osób '
    '(reservations.participants, guest_reservations.participants). Brak wiersza = nie wyceniono; '
    'wiersz z 0 = gratis. To rozróżnienie jest nośne i dlatego amount jest NOT NULL.';
