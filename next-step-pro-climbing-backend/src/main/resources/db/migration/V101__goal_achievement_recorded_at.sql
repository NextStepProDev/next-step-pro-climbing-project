-- Kiedy zaliczenie celu TRAFIŁO DO SYSTEMU — osobno od achieved_at, czyli od tego, kiedy cel
-- faktycznie osiągnięto. Trener często zalicza cel z opóźnieniem i cofa datę do prawdziwej;
-- zielona karta „Osiągnięty" (7 dni w banerze) liczona od achieved_at przepadała wtedy w
-- całości, więc trzeba było wybierać między prawdziwą historią a świętowaniem. Historia
-- (skrzynia trofeów) dalej idzie po achieved_at, świętowanie — po tej kolumnie.
ALTER TABLE athlete_goals
    ADD COLUMN achievement_recorded_at TIMESTAMPTZ;

-- Cele zaliczone przed tą migracją: moment zapisu nieznany, więc przyjmujemy datę osiągnięcia
-- — dokładnie dotychczasowe zachowanie, nic nie zaczyna nagle świecić.
UPDATE athlete_goals
   SET achievement_recorded_at = achieved_at
 WHERE achieved_at IS NOT NULL;

-- Obie daty ustawione albo obie puste (cofnięcie celu czyści obie)
ALTER TABLE athlete_goals
    ADD CONSTRAINT chk_athlete_goals_achievement_recorded
        CHECK ((achieved_at IS NULL) = (achievement_recorded_at IS NULL));
