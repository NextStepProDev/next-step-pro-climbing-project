-- Pieniądze usuniętego konta zostają w księdze — bez osoby.
--
-- Do tej pory settlements.user_id i payments.user_id miały ON DELETE CASCADE: usunięcie konta
-- abonenta zabierało jego wpłaty, więc przychód z minionych miesięcy po cichu malał.
-- Decyzja właściciela 2026-10-18: wpłaty zostają (RODO art. 17 ust. 3 lit. b — obowiązek
-- przechowywania dokumentacji rozliczeniowej), anonimowo: kwota, data, termin — bez imienia.
--
-- Należności i wpłaty przepina się na LOSOWY pseudonim (erased_payer_id), jeden na usunięte
-- konto, zamiast po prostu wyzerować user_id. Pseudonim trzyma razem wiersze tej samej osoby,
-- więc PaymentAllocator dzieli jej wpłaty na jej należności dokładnie tak jak wcześniej i podział
-- przychodu (sesje / wydarzenia / abonament) się nie zmienia. Z nikim go powiązać nie można:
-- wiersz users znika w tej samej operacji.
--
-- Robi to wyzwalacz, nie kod aplikacji: kont usuwa się trzema ścieżkami (samodzielnie, przez
-- admina, retencja niepotwierdzonych), a reguła na poziomie bazy obejmuje każdą, także przyszłą
-- — ten sam powód, dla którego log aktywności wymazuje osobę przez ON DELETE SET NULL.
-- BEFORE DELETE biegnie przed akcjami FK, więc CASCADE nie ma już czego zabrać.

ALTER TABLE settlements ADD COLUMN erased_payer_id UUID;
ALTER TABLE payments    ADD COLUMN erased_payer_id UUID;

ALTER TABLE settlements DROP CONSTRAINT chk_settlements_single_payer;
ALTER TABLE settlements ADD CONSTRAINT chk_settlements_single_payer CHECK (
    (user_id              IS NOT NULL)::int
  + (guest_reservation_id IS NOT NULL)::int
  + (erased_payer_id      IS NOT NULL)::int = 1);

-- Opłata miesięczna należy do konta albo do konta usuniętego — nigdy do gościa.
ALTER TABLE settlements DROP CONSTRAINT chk_settlements_period_payer;
ALTER TABLE settlements ADD CONSTRAINT chk_settlements_period_payer CHECK (
    period_month IS NULL OR user_id IS NOT NULL OR erased_payer_id IS NOT NULL);

ALTER TABLE payments DROP CONSTRAINT chk_payments_single_payer;
ALTER TABLE payments ADD CONSTRAINT chk_payments_single_payer CHECK (
    (user_id              IS NOT NULL)::int
  + (guest_reservation_id IS NOT NULL)::int
  + (erased_payer_id      IS NOT NULL)::int = 1);

CREATE FUNCTION keep_money_of_deleted_user() RETURNS trigger AS $$
DECLARE
    pseudonym UUID := gen_random_uuid();
BEGIN
    UPDATE settlements SET user_id = NULL, erased_payer_id = pseudonym WHERE user_id = OLD.id;
    UPDATE payments    SET user_id = NULL, erased_payer_id = pseudonym WHERE user_id = OLD.id;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_keep_money
    BEFORE DELETE ON users
    FOR EACH ROW EXECUTE FUNCTION keep_money_of_deleted_user();
