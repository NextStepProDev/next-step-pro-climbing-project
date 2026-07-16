-- Wątek komentarzy (czat zawodnik <-> trener) przy pojedynczym treningu osobistym.
-- author_is_admin = rola autora W MOMENCIE wpisu (odporne na późniejsze zmiany ról);
-- steruje też licznikami nieprzeczytanych (zawodnik liczy wpisy trenera i odwrotnie).
CREATE TABLE training_comments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    training_id     UUID NOT NULL REFERENCES personal_trainings(id) ON DELETE CASCADE,
    author_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    author_is_admin BOOLEAN NOT NULL,
    body            VARCHAR(1000) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Wątek ładowany per trening, chronologicznie.
CREATE INDEX idx_training_comments_training ON training_comments(training_id);
