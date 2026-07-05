-- Znacznik "przeczytane" dla powiadomień admina o nowych rezerwacjach.
-- Badge w panelu admina (zakładka Rezerwacje + link Admin w navbarze) liczy rezerwacje
-- CONFIRMED utworzone PO tym znaczniku; wejście w zakładkę Rezerwacje ustawia go na now().
-- Kolumna per-user (każdy admin ma własny stan przeczytania); dla zwykłych użytkowników nieużywana.
ALTER TABLE users ADD COLUMN admin_reservations_seen_at TIMESTAMPTZ NOT NULL DEFAULT now();
