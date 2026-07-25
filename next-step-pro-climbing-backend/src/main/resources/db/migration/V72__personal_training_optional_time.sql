-- Trening "na cały dzień" (styl TrainingPeaks): trener najczęściej zleca trening NA DZIEŃ,
-- nie na konkretną godzinę. Godzina staje się opcjonalna — untimed = start_time i end_time NULL.
-- Dozwolone tylko: oba NULL (cały dzień) ALBO oba ustawione (walidacja w serwisie).
-- Istniejące treningi zostają z godzinami (zero backfillu).
ALTER TABLE personal_trainings ALTER COLUMN start_time DROP NOT NULL;
ALTER TABLE personal_trainings ALTER COLUMN end_time   DROP NOT NULL;

-- Migawka usunięcia przyszłego treningu też musi znieść untimed (untimed można usunąć).
ALTER TABLE training_deletions ALTER COLUMN start_time DROP NOT NULL;
ALTER TABLE training_deletions ALTER COLUMN end_time   DROP NOT NULL;
