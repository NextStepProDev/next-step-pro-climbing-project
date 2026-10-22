-- Zajęcia BEZPŁATNE jako trzeci rodzaj oznaczenia terminu, obok kontrahenta (V93) i abonamentu (V95).
--
-- Oznaczenie w session_payouts znaczy „ten termin nie jest wyceniany per uczestnik”. Do tej pory
-- powodem był zawsze ktoś, kto płaci ryczałtem; darmowe zajęcia to ten sam stan bez płatnika.
-- Wpisywanie 0 zł każdemu uczestnikowi z osobna działało, ale termin bez kwoty wisiał w „Do wyceny”,
-- a każda nowa osoba na nim wymagała kolejnego zera.
--
-- Pojedyncza osoba gratis na PŁATNYM terminie dalej jest należnością 0 zł w jej wierszu — to
-- oznaczenie dotyczy całego terminu.
--
-- Unikaty (time_slot_id) i (event_id) z V93 zostają nietknięte i dalej niosą całą regułę: termin
-- ma JEDNO oznaczenie, więc bezpłatny nie może mieć jednocześnie płatnika.
ALTER TABLE session_payouts ADD COLUMN is_free BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE session_payouts DROP CONSTRAINT chk_session_payouts_single_payer;

ALTER TABLE session_payouts ADD CONSTRAINT chk_session_payouts_single_payer CHECK (
    (payout_source_id IS NOT NULL)::int + (user_id IS NOT NULL)::int + is_free::int = 1);

COMMENT ON COLUMN session_payouts.is_free IS
    'Zajecia bezplatne: termin bez wyceny i bez platnika. Dokladnie jedno z trzech: '
    'payout_source_id, user_id albo is_free.';
