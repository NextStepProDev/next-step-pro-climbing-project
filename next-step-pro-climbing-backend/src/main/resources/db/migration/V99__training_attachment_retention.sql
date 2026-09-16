-- Materiały-pliki przy treningu też przestają leżeć wiecznie: rok od podpięcia, jak załączniki
-- w wątku komentarzy (V80). Zdjęcie z zajęć sprzed dwóch lat nie jest już nikomu potrzebne,
-- a zajmuje ten sam dysk.
--
-- ⚠️ Komentarz nagłówkowy V80 mówi, że materiał trenera ma INNY cykl życia niż plik z rozmowy —
-- od tej migracji to nieprawda dla plików przy treningu. Tamtej migracji nie wolno poprawić
-- (checksum), więc sprostowanie mieszka w CLAUDE.md, przy „Materiały treningu".
--
-- Data jest ZAPISANA, nie wyliczana z created_at — ten sam powód co przy training_comment_files:
-- front pokazuje przy pliku prawdziwą datę, a późniejsza zmiana okna retencji nie przepisuje losu
-- plików, które ktoś już zobaczył z konkretnym terminem.
ALTER TABLE training_attachments ADD COLUMN expires_at TIMESTAMPTZ;

-- NULL = nie wygasa, i to jest cała reguła zakresu:
--   * LINK nie zajmuje dysku — nie ma czego kasować, a zniknięcie linku po roku byłoby kasowaniem
--     treści, nie pliku;
--   * SZABLON to biblioteka wielokrotnego użytku. PDF znikający po roku zostawia szablon po cichu
--     niekompletny, a trener dowie się o tym dopiero rozdając go zawodnikowi.
-- CHECK trzyma to strukturalnie, żeby nie dało się zapisać daty na wierszu, którego zamiatacz
-- i tak nie powinien ruszyć.
ALTER TABLE training_attachments ADD CONSTRAINT chk_training_attachments_expiry
    CHECK (expires_at IS NULL OR (kind = 'FILE' AND training_id IS NOT NULL));

-- Zastane pliki liczą rok od wgrania, nie od wdrożenia — decyzja właściciela. Skutek jest
-- zamierzony i jednorazowy: pierwszy bieg zamiatacza po wdrożeniu zabiera cały zaległy ogon naraz
-- (wszystko starsze niż rok), tak jak pierwszy bieg retencji niepotwierdzonych kont.
UPDATE training_attachments
   SET expires_at = created_at + INTERVAL '365 days'
 WHERE kind = 'FILE' AND training_id IS NOT NULL;

-- Zamiatacz pyta wyłącznie o wiersze z datą i tylko o te już przeterminowane; indeks częściowy,
-- bo połowa tabeli (linki, szablony) ma tu NULL na zawsze.
CREATE INDEX idx_training_attachments_expires ON training_attachments(expires_at)
    WHERE expires_at IS NOT NULL;
