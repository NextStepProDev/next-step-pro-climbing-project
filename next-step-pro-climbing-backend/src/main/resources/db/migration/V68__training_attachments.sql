-- Materiały do treningu: do 3 pozycji na trening. W tej migracji tylko LINKI
-- (url + opcjonalna etykieta). Linki YouTube/Instagram są w widoku renderowane jako
-- osadzony odtwarzacz (embed liczony w DTO, nie zapisywany). Kolejna migracja doda
-- kolumny plikowe (upload PDF/zdjęć) na tej samej tabeli.
CREATE TABLE training_attachments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    training_id UUID NOT NULL REFERENCES personal_trainings(id) ON DELETE CASCADE,
    url         TEXT NOT NULL,
    label       VARCHAR(120),
    position    INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Zakres kalendarza ładuje załączniki wielu treningów naraz (batch, bez N+1).
CREATE INDEX idx_training_attachments_training ON training_attachments(training_id);
