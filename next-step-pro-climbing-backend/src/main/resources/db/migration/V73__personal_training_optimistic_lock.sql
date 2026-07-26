-- Kalendarz treningowy to WSPÓŁDZIELONY plan trener↔zawodnik: obie strony mogą edytować ten sam
-- trening. Bez wersjonowania równoczesna edycja (np. trener zmienia tytuł, zawodnik oznacza
-- wykonanie) kończy się cichym nadpisaniem (last-write-wins). Kolumna `version` włącza optymistyczne
-- blokowanie JPA (@Version) — kolidujący zapis dostanie 409 zamiast po cichu zgubić zmianę.
ALTER TABLE personal_trainings ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
