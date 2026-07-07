-- Rezerwacje dodane ręcznie przez admina z panelu (zapis zarejestrowanego użytkownika na
-- slot/wydarzenie). Badge "nowe rezerwacje" je pomija — admin nie potrzebuje powiadomienia
-- o własnej akcji; kropka ma świecić tylko dla rezerwacji zrobionych przez klientów.
ALTER TABLE reservations ADD COLUMN created_by_admin BOOLEAN NOT NULL DEFAULT false;
