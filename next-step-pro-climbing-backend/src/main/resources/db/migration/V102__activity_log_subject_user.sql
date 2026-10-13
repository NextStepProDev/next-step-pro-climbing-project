-- Prawo do bycia zapomnianym (RODO art. 17) w logu aktywności admina.
--
-- Wpisy ADMIN_* leżą pod adminem (user_id = sprawca), więc kaskada z usunięcia konta ich nie
-- dosięga — a opis niósł „Imię Nazwisko (email)” osoby, której akcja dotyczyła. Po usunięciu
-- konta jej dane zostawały w tabeli na zawsze; wpis ADMIN_USER_DELETED zapisywał je wręcz
-- w chwili usunięcia.
--
-- Od teraz osoba, której dotyczy wpis, to FK z ON DELETE SET NULL, a nazwisko i e-mail czyta się
-- na żywo z users. Usunięcie konta wymazuje je z każdego wpisu samo, każdą ścieżką (własne
-- usunięcie, admin, retencja niepotwierdzonych) — bez kodu, o którym trzeba pamiętać.
-- W opisie zostaje wyłącznie kontekst bez danych osobowych (termin i tytuł treningu/celu,
-- kierunek zmiany flagi zawodnika).
ALTER TABLE activity_logs
    ADD COLUMN subject_user_id UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX idx_activity_logs_subject_user
    ON activity_logs(subject_user_id) WHERE subject_user_id IS NOT NULL;

-- Dowiązanie istniejących wpisów: opis zawierał „(email)” osoby.
UPDATE activity_logs a
   SET subject_user_id = u.id
  FROM users u
 WHERE a.action_type IN ('ADMIN_USER_MAKE_ADMIN', 'ADMIN_USER_ADMIN_REMOVED',
                         'ADMIN_USER_FORCE_LOGOUT', 'ADMIN_USER_ATHLETE_TOGGLED',
                         'ADMIN_USER_DATA_EXPORTED',
                         'ADMIN_TRAINING_CREATED', 'ADMIN_TRAINING_UPDATED', 'ADMIN_TRAINING_DELETED',
                         'ADMIN_GOAL_CREATED', 'ADMIN_GOAL_UPDATED', 'ADMIN_GOAL_DELETED',
                         'ADMIN_GOAL_ACHIEVED', 'ADMIN_GOAL_REOPENED')
   AND a.description IS NOT NULL
   AND position('(' || u.email || ')' IN a.description) > 0;

-- Wpisy treningu/celu, przy których zawodnika nie dało się wczytać, zaczynały się od jego UUID.
UPDATE activity_logs a
   SET subject_user_id = u.id
  FROM users u
 WHERE a.subject_user_id IS NULL
   AND a.action_type IN ('ADMIN_TRAINING_CREATED', 'ADMIN_TRAINING_UPDATED', 'ADMIN_TRAINING_DELETED',
                         'ADMIN_GOAL_CREATED', 'ADMIN_GOAL_UPDATED', 'ADMIN_GOAL_DELETED',
                         'ADMIN_GOAL_ACHIEVED', 'ADMIN_GOAL_REOPENED')
   AND a.description LIKE u.id::text || ' — %';

-- Zdjęcie danych osobowych z opisów — także tam, gdzie konta już nie ma (to są właśnie wpisy,
-- dla których ta migracja powstała).
UPDATE activity_logs
   SET description = NULL
 WHERE action_type IN ('ADMIN_USER_MAKE_ADMIN', 'ADMIN_USER_ADMIN_REMOVED',
                       'ADMIN_USER_FORCE_LOGOUT', 'ADMIN_USER_DATA_EXPORTED', 'ADMIN_USER_DELETED');

-- „Imię (email) -> athlete” → „athlete”
UPDATE activity_logs
   SET description = CASE WHEN description LIKE '% -> %'
                          THEN regexp_replace(description, '^.* -> ', '')
                          ELSE NULL END
 WHERE action_type = 'ADMIN_USER_ATHLETE_TOGGLED'
   AND description IS NOT NULL;

-- „Imię (email) — 2026-10-01 Siła” → „2026-10-01 Siła”. Pierwszy separator, nie ostatni: tytuł
-- treningu może sam zawierać „ — ”. Opis bez separatora nie ma czego zachować, więc znika cały.
UPDATE activity_logs
   SET description = CASE WHEN description LIKE '% — %'
                          THEN regexp_replace(description, '^.*? — ', '')
                          ELSE NULL END
 WHERE action_type IN ('ADMIN_TRAINING_CREATED', 'ADMIN_TRAINING_UPDATED', 'ADMIN_TRAINING_DELETED',
                       'ADMIN_GOAL_CREATED', 'ADMIN_GOAL_UPDATED', 'ADMIN_GOAL_DELETED',
                       'ADMIN_GOAL_ACHIEVED', 'ADMIN_GOAL_REOPENED')
   AND description IS NOT NULL;
