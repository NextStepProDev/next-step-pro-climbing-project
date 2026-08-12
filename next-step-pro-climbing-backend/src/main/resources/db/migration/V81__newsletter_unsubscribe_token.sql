-- Stały token do linku wypisu z newslettera, jedna kolumna na userze zamiast wiersza w auth_tokens.
--
-- Wcześniej token był hashowany w auth_tokens i KASOWANY przy każdej wysyłce, więc wypisać można
-- się było wyłącznie z ostatniego maila — każdy starszy link odpowiadał „nieprawidłowy link", a
-- człowiekowi zostawało zalogowanie się i szukanie ustawienia. Ludzie odchodzą z listy, gdy poczta
-- się nazbiera, i klikają w wiadomość, którą akurat mają otwartą, rzadko w najnowszą.
--
-- Reużycie starego tokenu było niemożliwe przy hashowaniu (wartości z maila nie da się odczytać z
-- bazy), więc token przenosi się na usera: jeden na zawsze, ten sam w każdym mailu, wszystkie
-- wysłane linki działają. Cena: tokenu nie da się unieważnić inaczej niż ręczną podmianą.
ALTER TABLE users ADD COLUMN newsletter_unsubscribe_token UUID NOT NULL DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX idx_users_newsletter_unsubscribe_token ON users (newsletter_unsubscribe_token);

-- Stare tokeny nie mają już czego obsługiwać. Linki z maili wysłanych przed tą migracją przestają
-- działać — nie do uniknięcia przy zmianie mechanizmu, a i tak działał z nich tylko najnowszy.
DELETE FROM auth_tokens WHERE token_type = 'NEWSLETTER_UNSUBSCRIBE';
