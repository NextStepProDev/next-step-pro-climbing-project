-- Prośba zapisanej osoby o zmianę terminu jej rezerwacji. Trener ją akceptuje albo odrzuca;
-- akceptacja przesuwa CAŁY termin (slot albo wydarzenie) razem ze wszystkimi zapisanymi.
--
-- Osobna tabela, nie kolejny rodzaj training_requests: propozycja terminu opisuje coś, czego
-- jeszcze nie ma (akceptacja TWORZY slot), a ta prośba wskazuje istniejący termin i akceptacja go
-- PRZESUWA. Wspólne kolumny byłyby w połowie wierszy puste z dwóch różnych powodów.
--
-- ⚠️ Celem jest para (termin, osoba), NIGDY reservation_id — ta sama reguła co przy wątkach (V97)
-- i rozliczeniach (V92): zapis na wielodniowe wydarzenie zakłada jeden wiersz reservations NA
-- DZIEŃ, a anulowanie i ponowny zapis to nowy wiersz. Rezerwacja jest tylko adresem na drucie.
-- Trzy prawdziwe FK z ON DELETE CASCADE: skasowanie terminu albo konta zabiera prośbę.
CREATE TABLE reschedule_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id)      ON DELETE CASCADE,
    time_slot_id    UUID          REFERENCES time_slots(id) ON DELETE CASCADE,
    event_id        UUID          REFERENCES events(id)     ON DELETE CASCADE,

    -- Migawka terminu w chwili prośby. Nie ozdoba: jeśli trener przesunie termin sam, zanim
    -- odpowie, akceptacja "z 21.10 na 23.10" opisywałaby termin, którego już nie ma — serwis
    -- porównuje migawkę z żywym terminem i odmawia (409). Mail o akceptacji też ją czyta.
    old_date        DATE NOT NULL,
    old_end_date    DATE,
    old_start_time  TIME,
    old_end_time    TIME,

    new_date        DATE NOT NULL,
    new_end_date    DATE,
    new_start_time  TIME,
    new_end_time    TIME,

    -- HTML-escaped przy zapisie (jak komentarz propozycji terminu); jedzie mailem do trenera.
    comment         VARCHAR(1000),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    admin_note      VARCHAR(500),
    resolved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_reschedule_requests_single_target CHECK (
        (time_slot_id IS NOT NULL)::int + (event_id IS NOT NULL)::int = 1
    ),
    CONSTRAINT chk_reschedule_requests_status CHECK (
        status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'WITHDRAWN')
    ),
    -- Slot ma zawsze godziny i jeden dzień; wydarzenie ma zawsze dzień końca, a godziny oba-albo-żaden
    -- (NULL = całodniowe, jak events.start_time/end_time).
    CONSTRAINT chk_reschedule_requests_slot_shape CHECK (
        time_slot_id IS NULL OR (
            old_end_date IS NULL AND new_end_date IS NULL
            AND old_start_time IS NOT NULL AND old_end_time IS NOT NULL
            AND new_start_time IS NOT NULL AND new_end_time IS NOT NULL
        )
    ),
    CONSTRAINT chk_reschedule_requests_event_shape CHECK (
        event_id IS NULL OR (
            old_end_date IS NOT NULL AND new_end_date IS NOT NULL
            AND new_end_date >= new_date
            AND (old_start_time IS NULL) = (old_end_time IS NULL)
            AND (new_start_time IS NULL) = (new_end_time IS NULL)
        )
    )
);

-- Najwyżej jedna oczekująca prośba na parę (termin, osoba).
CREATE UNIQUE INDEX uq_reschedule_requests_pending_slot
    ON reschedule_requests(user_id, time_slot_id) WHERE status = 'PENDING' AND time_slot_id IS NOT NULL;
CREATE UNIQUE INDEX uq_reschedule_requests_pending_event
    ON reschedule_requests(user_id, event_id) WHERE status = 'PENDING' AND event_id IS NOT NULL;

-- Panel trenera i licznik w navbarze pytają o PENDING.
CREATE INDEX idx_reschedule_requests_status ON reschedule_requests(status);
CREATE INDEX idx_reschedule_requests_user   ON reschedule_requests(user_id);
CREATE INDEX idx_reschedule_requests_slot   ON reschedule_requests(time_slot_id);
CREATE INDEX idx_reschedule_requests_event  ON reschedule_requests(event_id);
