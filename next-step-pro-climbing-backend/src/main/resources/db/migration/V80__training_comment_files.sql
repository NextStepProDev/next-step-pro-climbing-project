-- Załączniki w rozmowie zawodnik <-> trener: zdjęcie drogi, zrzut ekranu z zegarka, PDF z rozpiską.
--
-- Osobna tabela, a NIE kolumny na training_comments i NIE nowy właściciel w training_attachments:
--
--   * kolumny dałyby dokładnie jeden plik na wiadomość, a seria zdjęć jednej sekwencji to
--     naturalny sposób pokazania, co poszło nie tak;
--   * training_attachments niesie materiały TRENERA i jest kopiowane przez duplikat treningu
--     oraz użycie szablonu. Załącznik z rozmowy nie ma prawa podróżować z kopiowanym planem,
--     a doklejenie trzeciego właściciela do tabeli, która ma już parę training/template,
--     rozmnaża miejsca wymagające rozgałęzienia po właścicielu.
--
-- Zdjęcie z komentarza ma też inny cykl życia niż materiał trenera: znika po roku (patrz expires_at).
-- Dwie różne polityki retencji w jednej tabeli to zaproszenie do skasowania niewłaściwych danych.

-- Wiadomość może być samym załącznikiem ("zobacz, tak to wyszło") — obrazek bywa całym komunikatem.
-- CHECK-a "niepusty komentarz" nie da się tu postawić, bo warunek sięga drugiej tabeli; pilnuje go
-- TrainingCommentFileService przy tworzeniu i przy kasowaniu ostatniego pliku (+ testy).
ALTER TABLE training_comments ALTER COLUMN body DROP NOT NULL;

CREATE TABLE training_comment_files (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    comment_id    UUID NOT NULL REFERENCES training_comments(id) ON DELETE CASCADE,
    filename      VARCHAR(64)  NOT NULL,
    original_name VARCHAR(255),
    mime_type     VARCHAR(100) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    -- Wymiary obrazu zapisane przy wgraniu, żeby miniatura mogła zarezerwować miejsce ZANIM bajty
    -- dojdą — inaczej wątek skacze w trakcie doczytywania. PDF nie ma wymiarów, stąd "oba albo żadne".
    width         SMALLINT,
    height        SMALLINT,
    position      SMALLINT     NOT NULL DEFAULT 0,
    -- Data wygaśnięcia jest ZAPISANA, a nie wyliczana z created_at + stała. Dwa powody: użytkownikowi
    -- pokazujemy prawdziwą datę zamiast odtwarzać ją ze stałej po obu stronach, a późniejsza zmiana
    -- okna retencji nie przepisuje po cichu losu plików wgranych na starych zasadach.
    expires_at    TIMESTAMPTZ  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_tcf_position   CHECK (position BETWEEN 0 AND 2),
    CONSTRAINT chk_tcf_dimensions CHECK (num_nonnulls(width, height) <> 1)
);

CREATE UNIQUE INDEX uq_tcf_comment_pos ON training_comment_files (comment_id, position);

-- Jeden wiersz = dokładnie jeden plik na dysku. Materiały trenera są współdzielone przez duplikat
-- i szablon, więc tam kasowanie wymaga zliczania referencji; tutaj unikat czyni regułę
-- "znikł wiersz => znikł plik" niepodważalną, zamiast polegać na tym, że nikt nie doda kopiowania.
CREATE UNIQUE INDEX uq_tcf_filename ON training_comment_files (filename);

CREATE INDEX idx_tcf_expiry ON training_comment_files (expires_at);
