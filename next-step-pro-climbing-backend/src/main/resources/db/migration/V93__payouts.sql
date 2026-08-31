-- Drugi rodzaj pieniędzy: praca opłacana ZBIORCZO przez kogoś innego (szkoła, klub), a nie
-- wyceniana per uczestnik. Zajęcia dodatkowe: prowadzisz je przez miesiąc, a wypłatę dostajesz raz,
-- po fakcie, i do ostatniej chwili nie wiesz, ile wyjdzie netto.
--
-- ⚠️ To NIE JEST wariant tabeli settlements i nie wolno go tam wcisnąć. Tam jednostką jest para
-- (termin, płatnik) i kwota istnieje w momencie zajęć; tutaj kwota nie istnieje aż do przelewu,
-- płatnikiem nie jest uczestnik, a jedna płatność pokrywa kilkanaście terminów. Wciśnięcie tego
-- w tamten kształt znaczyłoby wpisywanie zmyślonej kwoty per głowa w chwili, w której się jej nie
-- zna — czyli dokładnie tę nieprawdziwą liczbę, przed którą broni cała reszta tej funkcji.
--
-- Trzy tabele, bo to trzy różne byty: KTO płaci, KTÓRE zajęcia są dla niego, i CO wpłynęło.
-- Dopiero ich zestawienie daje liczbę, dla której warto to prowadzić: 12 zajęć / 1400 zł = 117 zł
-- za zajęcia, czyli ile realnie płaci dane miejsce.

-- Kontrahent płacący zbiorczo.
CREATE TABLE payout_sources (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(120) NOT NULL,

    -- Archiwizacja zamiast usuwania: wypłaty i przypisania wskazują na to źródło, a współpraca,
    -- która się skończyła, nie unieważnia zarobionych w niej pieniędzy. Znacznik czasu, nie boolean
    -- — dom tego repozytorium.
    archived_at TIMESTAMPTZ,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_payout_sources_name_not_blank CHECK (btrim(name) <> '')
);

-- Nazwa unikalna tylko wśród AKTYWNYCH: zarchiwizowanie „Klub XYZ" i założenie go ponownie po roku
-- jest normalne, a kolizja z martwym wierszem byłaby odmową bez powodu.
CREATE UNIQUE INDEX uq_payout_sources_active_name
    ON payout_sources (lower(name)) WHERE archived_at IS NULL;

-- Które zajęcia są pracą dla którego kontrahenta.
--
-- Osobna tabela, nie kolumna na time_slots/events, i to jest ta sama decyzja co przy settlements:
-- tamte kształty lecą anonimom i CACHE'UJĄ SIĘ pod calendarMonth/Week/Day, więc każde dopisane im
-- pole wychodzi na zewnątrz domyślnie. Tutaj wyciekłaby nazwa szkoły przy publicznym terminie.
CREATE TABLE session_payouts (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    time_slot_id     UUID REFERENCES time_slots(id) ON DELETE CASCADE,
    event_id         UUID REFERENCES events(id)     ON DELETE CASCADE,
    payout_source_id UUID NOT NULL REFERENCES payout_sources(id) ON DELETE CASCADE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_session_payouts_single_target CHECK (
        (time_slot_id IS NOT NULL)::int + (event_id IS NOT NULL)::int = 1)
);

-- Jeden termin należy do jednego kontrahenta.
CREATE UNIQUE INDEX uq_session_payouts_slot
    ON session_payouts (time_slot_id) WHERE time_slot_id IS NOT NULL;
CREATE UNIQUE INDEX uq_session_payouts_event
    ON session_payouts (event_id) WHERE event_id IS NOT NULL;
-- FK bez indeksu wiodącego: liczenie zajęć per kontrahent i CASCADE przy archiwizacji idą po nim.
CREATE INDEX idx_session_payouts_source ON session_payouts (payout_source_id);

-- Co faktycznie wpłynęło.
CREATE TABLE payouts (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payout_source_id UUID NOT NULL REFERENCES payout_sources(id) ON DELETE CASCADE,

    -- Pierwszy dzień miesiąca, którego wypłata dotyczy. DATE, nie para (rok, miesiąc): ta sama
    -- etykieta dnia co wszędzie indziej, więc front parsuje ją tym samym parseCalendarDate.
    period_month     DATE NOT NULL,

    -- To, co wpłynęło NA KONTO. Netto jest tu jednostką naturalną, bo jest jedyną liczbą, którą
    -- naprawdę się ma — brutto i potrącenia to księgowość płatnika, nie ta aplikacja.
    amount           NUMERIC(10,2) NOT NULL,

    -- Kiedy przyszło. Przychód liczy się po TEJ dacie, tak samo jak settlements.settled_on, więc
    -- miesięczna suma nadal ma jedną oś niezależnie od tego, którym kanałem przyszły pieniądze.
    received_on      DATE NOT NULL,

    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_payouts_amount_range CHECK (amount >= 0 AND amount <= 1000000),
    CONSTRAINT chk_payouts_period_is_month_start CHECK (EXTRACT(DAY FROM period_month) = 1)
);

-- ⚠️ Świadomie BEZ unikatu na (źródło, miesiąc). Kusi, bo wypłata jest zwykle jedna — ale wyrównanie,
-- druga transza albo korekta za ten sam miesiąc zdarzają się naprawdę, a odmowa ich zapisania
-- zmusiłaby do doklejenia kwoty do istniejącego wiersza i utraty informacji, że przyszły osobno.
-- Ekran sumuje wiersze i pokazuje ich liczbę, więc podwójne wpisanie widać.
CREATE INDEX idx_payouts_source_period ON payouts (payout_source_id, period_month);
CREATE INDEX idx_payouts_received ON payouts (received_on);

COMMENT ON TABLE payouts IS
    'Wypłata zbiorcza od kontrahenta za okres. Przychód liczony po received_on, spójnie z '
    'settlements.settled_on. Kwota to netto, które wpłynęło.';

COMMENT ON TABLE session_payouts IS
    'Które terminy są pracą dla którego kontrahenta. Pozwala zestawić liczbę zajęć w miesiącu z '
    'wypłatą za ten miesiąc i wyliczyć realną stawkę. Osobna tabela, żeby nazwa kontrahenta nie '
    'trafiła do cache''owanych DTO kalendarza.';
