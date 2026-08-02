-- Wyraźna zgoda na przetwarzanie danych kalendarza treningowego (RODO art. 9 ust. 2 lit. a).
--
-- Kalendarz zbiera dane, których reszta serwisu nie zbiera: pomiary wagi z datami, cele wagowe,
-- ocenę zmęczenia RPE i opisowy feedback po treningu. W tym kontekście (codzienny pomiar +
-- trend + automatyczny sygnał szybkiej utraty masy dla trenera) traktujemy je jako dane
-- dotyczące zdrowia, a te wymagają zgody WYRAŹNEJ — czyli odrębnej czynności użytkownika,
-- a nie domniemania z faktu korzystania z serwisu ani z akceptacji regulaminu.
--
-- NULL = zgody brak. Kolumna startuje pusta CELOWO, także dla zawodników, którzy kalendarza
-- używają od dawna: przy najbliższym wejściu każdy z nich raz przechodzi przez ekran zgody.
-- Sam timestamp jest dowodem zgody wymaganym przez RODO (jak newsletter_subscribed_at).
--
-- Zgoda jest przypięta do statusu zawodnika: odebranie is_athlete czyści ją (AdminService),
-- więc ponowne włączenie kalendarza po przerwie znów wymaga świadomej decyzji.
ALTER TABLE users
    ADD COLUMN training_consent_at TIMESTAMPTZ;
