-- Abonament: stała opłata miesięczna za PROWADZENIE, nie za pojedynczy trening.
--
-- Trzeci kształt pieniędzy w tej aplikacji i — w odróżnieniu od wypłat zbiorczych — NIE jest nowym
-- bytem, tylko trzecim celem rozliczenia. Powód jest praktyczny: opłata za prowadzenie może być
-- NALEŻNA, nie tylko zapłacona, więc musi trafiać do „Do odzyskania", grupować się z resztą długów
-- tej osoby i rozliczać jednym kliknięciem razem z jej dodatkowymi sesjami. Osobna tabela dałaby
-- drugą kolejkę długów, drugie sumowanie przychodu i drugą listę w karcie użytkownika.
--
-- ⚠️ Dlatego cel rozliczenia to teraz slot XOR wydarzenie XOR MIESIĄC, a nie dwa pierwsze.
-- Miesiąc nie jest kluczem obcym i nie ma czego kasować kaskadą — adresem jest para
-- (user_id, period_month), a kaskada po user_id i tak zabiera wiersz razem z kontem.
ALTER TABLE settlements ADD COLUMN period_month DATE;

ALTER TABLE settlements DROP CONSTRAINT chk_settlements_single_target;
ALTER TABLE settlements ADD CONSTRAINT chk_settlements_single_target CHECK (
    (time_slot_id IS NOT NULL)::int
  + (event_id     IS NOT NULL)::int
  + (period_month IS NOT NULL)::int = 1);

-- Opłata miesięczna należy do KONTA, nie do gościa: abonament zakłada ciągłość współpracy, a gość
-- jest jednorazowym wpisem bez tożsamości, którą dałoby się przenieść na następny miesiąc.
ALTER TABLE settlements ADD CONSTRAINT chk_settlements_period_payer CHECK (
    period_month IS NULL OR user_id IS NOT NULL);

ALTER TABLE settlements ADD CONSTRAINT chk_settlements_period_is_month_start CHECK (
    period_month IS NULL OR EXTRACT(DAY FROM period_month) = 1);

-- Jedna opłata na osobę na miesiąc. To ten unikat czyni naliczanie IDEMPOTENTNYM: zadanie może
-- puścić się dwa razy tego samego dnia albo nadrobić trzy zaległe miesiące naraz i nie zdubluje nic.
CREATE UNIQUE INDEX uq_settlements_period_user
    ON settlements (user_id, period_month) WHERE period_month IS NOT NULL;

-- Sam abonament: co i komu naliczać.
CREATE TABLE subscriptions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    amount      NUMERIC(10,2) NOT NULL,

    -- Pierwszy miesiąc naliczania i ostatni. Oba to pierwszy dzień miesiąca — abonament jest
    -- miesięczny, więc dzień nie niesie tu żadnej informacji poza pozorem precyzji.
    started_on  DATE NOT NULL,

    -- NULL = na czas nieokreślony. Data w PRZESZŁOŚCI jest dozwolona i jest funkcją, nie wypadkiem:
    -- współpracę zwykle kończy się w rozmowie, a wpisuje tydzień później.
    ended_on    DATE,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_subscriptions_amount_range CHECK (amount >= 0 AND amount <= 100000),
    CONSTRAINT chk_subscriptions_months_are_month_starts CHECK (
        EXTRACT(DAY FROM started_on) = 1 AND (ended_on IS NULL OR EXTRACT(DAY FROM ended_on) = 1)),
    CONSTRAINT chk_subscriptions_end_after_start CHECK (ended_on IS NULL OR ended_on >= started_on)
);

-- Jeden AKTYWNY abonament na osobę. Zakończone zostają — miesiące, które naliczyły, dalej na nie
-- wskazują jako na powód, dla którego powstały.
CREATE UNIQUE INDEX uq_subscriptions_active_user
    ON subscriptions (user_id) WHERE ended_on IS NULL;

CREATE INDEX idx_subscriptions_user ON subscriptions (user_id);

COMMENT ON COLUMN subscriptions.ended_on IS
    'Ostatni naliczany miesiąc. NULL = bezterminowy. Data wsteczna jest dozwolona; serwis kasuje '
    'wtedy jeszcze NIEZAPLACONE oplaty za miesiace zaczynajace sie po niej, a zaplacone zostawia — '
    'pieniadze wplynely i aplikacja nie ma tego cofac.';

COMMENT ON COLUMN settlements.period_month IS
    'Miesiac, za ktory nalezy sie oplata za prowadzenie. Trzeci rodzaj celu obok slotu i wydarzenia.';
