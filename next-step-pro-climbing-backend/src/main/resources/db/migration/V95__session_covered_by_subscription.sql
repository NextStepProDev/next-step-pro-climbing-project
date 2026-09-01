-- Sesja objęta ABONAMENTEM klienta, a nie tylko wypłatą od instytucji.
--
-- Do tej pory oznaczenie „ta sesja jest rozliczana zbiorczo" umiało wskazać wyłącznie kontrahenta
-- (szkołę, klub). Abonament z V94 jest tym samym zjawiskiem widzianym od drugiej strony: ktoś płaci
-- ryczałtem za okres, a te sesje są tym, co ten ryczałt pokrył. Różni je tylko to, KTO płaci —
-- instytucja bez konta czy klient, który konto ma.
--
-- ⚠️ Powód, dla którego to w ogóle powstało: bez oznaczenia sesja objęta abonamentem wisiała w
-- „Do wyceny" bez końca, a jedynym wyjściem było wpisanie 0 — co psuje rozróżnienie „gratis" od
-- „w abonamencie" i każe podziałowi przychodu twierdzić, że na treningach 1:1 zarabiasz zero.
ALTER TABLE session_payouts ADD COLUMN user_id UUID REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE session_payouts ALTER COLUMN payout_source_id DROP NOT NULL;

ALTER TABLE session_payouts ADD CONSTRAINT chk_session_payouts_single_payer CHECK (
    (payout_source_id IS NOT NULL)::int + (user_id IS NOT NULL)::int = 1);

-- Unikaty na (time_slot_id) i (event_id) z V93 zostają nietknięte i dalej niosą całą regułę:
-- JEDNA sesja należy do JEDNEGO płatnika, niezależnie od tego, którego rodzaju.
CREATE INDEX idx_session_payouts_user ON session_payouts (user_id);

COMMENT ON COLUMN session_payouts.user_id IS
    'Klient, ktorego abonament pokrywa te sesje. XOR z payout_source_id: sesja nalezy do jednego '
    'platnika, a te dwa rodzaje to instytucja platąca ryczaltem i klient z abonamentem.';
