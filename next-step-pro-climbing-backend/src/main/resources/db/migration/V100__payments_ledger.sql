-- Księga wpłat: to, co klient WRĘCZYŁ, jako zapis, którego nic już nie przepisuje.
--
-- Do tej pory settlements.paid_amount odpowiadało naraz na dwa pytania: „ile ta osoba dała"
-- i „na które zajęcia te pieniądze idą". Spłata zaległości musiała odpowiedzieć na drugie,
-- przepisując pierwsze. Zgłoszenie właściciela 2026-10-02: 20 zł zaległości, zajęcia za 140,
-- klientka daje 200. Po „Zapisz i spłać zaległość" dzisiejszy wiersz mówił „otrzymano 140",
-- a stary niósł +40 z dzisiejszą datą. Saldo było dobre, ale oba wiersze kłamały, a „poprawka"
-- 140 → 200 liczyła te same banknoty drugi raz.
--
-- Od teraz:
--   settlements.amount  = ile ktoś był winien za termin (należność) — bez zmian,
--   payments            = co i kiedy wpłynęło — niezmienne; korekta = usuń i wpisz od nowa,
--   podział wpłat na należności wylicza aplikacja (PaymentAllocator, od najstarszej) i NIGDZIE
--   go nie zapisujemy. Zapisany podział byłby dokładnie tym drugim źródłem prawdy, które
--   rozjechało się w zgłoszeniu.
--
-- Płatnik jak w settlements: konto XOR gość. Wpłata wisi na OSOBIE, nie na terminie — pieniądze
-- są wspólne dla wszystkich jej należności, więc skasowanie terminu nie może ich zabrać.
-- entered_* mówi tylko, PRZY KTÓRYM terminie wpłatę wpisano (żeby modal pokazał ją tam, gdzie ją
-- wpisano), i dlatego ma ON DELETE SET NULL, a nie CASCADE.
CREATE TABLE payments (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID REFERENCES users(id)              ON DELETE CASCADE,
    guest_reservation_id UUID REFERENCES guest_reservations(id) ON DELETE CASCADE,

    -- Zero nie jest wpłatą: „gratis" to należność 0 zł, nie wpłata 0 zł.
    amount               NUMERIC(10,2) NOT NULL,

    -- Etykieta dnia PL, jak settled_on wcześniej: oś przychodu.
    received_on          DATE NOT NULL,

    entered_slot_id      UUID REFERENCES time_slots(id) ON DELETE SET NULL,
    entered_event_id     UUID REFERENCES events(id)     ON DELETE SET NULL,

    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_payments_single_payer CHECK (
        (user_id              IS NOT NULL)::int
      + (guest_reservation_id IS NOT NULL)::int = 1),

    -- Kontekst najwyżej jeden; brak = wpłata przyjęta z listy „Do odzyskania" albo z migracji
    -- opłaty abonamentowej, która nie ma terminu w kalendarzu.
    CONSTRAINT chk_payments_single_context CHECK (
        (entered_slot_id  IS NOT NULL)::int
      + (entered_event_id IS NOT NULL)::int <= 1),

    CONSTRAINT chk_payments_amount_range CHECK (amount > 0 AND amount <= 100000)
);

-- Alokacja czyta wszystkie wpłaty jednej osoby; Postgres nie indeksuje FK sam, a CASCADE przy
-- kasowaniu konta/gościa też musi te wiersze znaleźć.
CREATE INDEX idx_payments_user  ON payments (user_id)              WHERE user_id IS NOT NULL;
CREATE INDEX idx_payments_guest ON payments (guest_reservation_id) WHERE guest_reservation_id IS NOT NULL;
CREATE INDEX idx_payments_received_on ON payments (received_on);
-- SET NULL przy kasowaniu terminu musi znaleźć wiersze bez seq scanu.
CREATE INDEX idx_payments_entered_slot  ON payments (entered_slot_id)  WHERE entered_slot_id IS NOT NULL;
CREATE INDEX idx_payments_entered_event ON payments (entered_event_id) WHERE entered_event_id IS NOT NULL;

-- Przeniesienie danych: każdy wiersz, na który coś wpłynęło, daje JEDNĄ wpłatę na kwotę, którą
-- dziś niesie. Nie da się odtworzyć prawdziwej historii wierszy już przepisanych przez pulę
-- (to jest właśnie ta utracona informacja) — sumy zostają te same co do grosza, a pojedyncze
-- wpisy poprawia się teraz ręcznie: usuń i wpisz prawdziwą kwotę z prawdziwego dnia.
--
-- settled_on jest ustawione przy każdym paid_amount > 0 (serwis tego wymagał), ale COALESCE na
-- datę terminu jest siatką: NOT NULL na received_on wywróciłby całą migrację na jednym wierszu.
INSERT INTO payments (user_id, guest_reservation_id, amount, received_on,
                      entered_slot_id, entered_event_id, created_at)
SELECT s.user_id,
       s.guest_reservation_id,
       s.paid_amount,
       COALESCE(s.settled_on, ts.date, e.start_date, s.period_month),
       s.time_slot_id,
       s.event_id,
       s.created_at
FROM settlements s
LEFT JOIN time_slots ts ON ts.id = s.time_slot_id
LEFT JOIN events e      ON e.id  = s.event_id
WHERE s.paid_amount > 0;

-- Stare kolumny znikają w tej samej migracji. Zostawione „na wszelki wypadek" byłyby drugą,
-- nieaktualną odpowiedzią na pytanie „ile zapłacił", czekającą na pierwszego, kto je przeczyta.
DROP INDEX IF EXISTS idx_settlements_underpaid;
DROP INDEX IF EXISTS idx_settlements_overpaid;
ALTER TABLE settlements DROP CONSTRAINT IF EXISTS chk_settlements_paid_range;
ALTER TABLE settlements DROP COLUMN paid_amount;
ALTER TABLE settlements DROP COLUMN settled_on;

COMMENT ON TABLE settlements IS
    'Naleznosc dla pary (cel, platnik): slot, wydarzenie albo miesiac abonamentu. Co wplynelo, '
    'trzyma tabela payments; podzial wplat na naleznosci jest wyliczany (od najstarszej), nigdy '
    'zapisywany.';

COMMENT ON TABLE payments IS
    'Wplaty — co i kiedy klient wreczyl. Niezmienne: korekta to usuniecie i nowy wpis. Wisza na '
    'osobie, nie na terminie; entered_* to tylko miejsce, w ktorym wplate wpisano.';
