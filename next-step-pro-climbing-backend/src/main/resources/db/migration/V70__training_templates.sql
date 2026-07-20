-- Szablony treningów trenera: wspólna biblioteka wielokrotnego użytku (tytuł, opis,
-- domyślny czas trwania + materiały). Użycie szablonu KOPIUJE treść do treningu — późniejsza
-- edycja szablonu nie zmienia rozdanych treningów.
CREATE TABLE training_templates (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title                    VARCHAR(150) NOT NULL,
    description              VARCHAR(2000),
    default_duration_minutes INT NOT NULL CHECK (default_duration_minutes BETWEEN 15 AND 720),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Materiały współdzielą tabelę training_attachments: wiersz należy ALBO do treningu,
-- ALBO do szablonu (CHECK dokładnie jeden właściciel). Zero duplikacji encji/walidacji.
ALTER TABLE training_attachments ALTER COLUMN training_id DROP NOT NULL;
ALTER TABLE training_attachments ADD COLUMN template_id UUID REFERENCES training_templates(id) ON DELETE CASCADE;
ALTER TABLE training_attachments ADD CONSTRAINT chk_training_attachments_parent
    CHECK (num_nonnulls(training_id, template_id) = 1);
CREATE INDEX idx_training_attachments_template ON training_attachments(template_id);
