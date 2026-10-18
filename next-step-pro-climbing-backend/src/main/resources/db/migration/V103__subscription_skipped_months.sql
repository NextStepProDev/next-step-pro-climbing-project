-- Miesiące, za które abonament świadomie NIE nalicza opłaty.
--
-- Kasowanie opłaty miesięcznej było do tej pory odwracane przez samą aplikację: nocne naliczanie
-- (`billUpTo`) nadrabia każdy pokryty miesiąc bez wiersza, więc opłata skasowana jako pomyłka
-- wracała następnej nocy — po AKTUALNEJ kwocie abonamentu. Kosz przy opłacie obiecywał coś, czego
-- system nie dotrzymywał.
--
-- Decyzja właściciela (2026-10-18): skasowanie opłaty znaczy „za ten miesiąc ta osoba nie płaci".
-- Ten fakt musi być ZAPISANY, bo brak wiersza w `settlements` nie odróżnia „pominięty celowo" od
-- „jeszcze nie naliczony" — a dokładnie tę różnicę musi znać naliczanie, żeby nadrabiać zaległe
-- miesiące (kontener, który nie wstał pierwszego) i nie wskrzeszać skasowanych.
--
-- Adresem jest para (user_id, period_month), jak przy samej opłacie — nie abonament. Abonamenty
-- jednej osoby nie mogą na siebie zachodzić, więc miesiąc wskazuje najwyżej jeden, a wpis
-- przeżywa „Zakończ" i „Wznów" bez przepisywania. Kaskada po koncie zabiera go razem z resztą.
CREATE TABLE subscription_skipped_months (
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    period_month DATE NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (user_id, period_month),
    CONSTRAINT chk_subscription_skipped_months_month_start CHECK (EXTRACT(DAY FROM period_month) = 1)
);
