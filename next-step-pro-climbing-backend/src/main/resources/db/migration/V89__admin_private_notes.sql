-- Prywatny notatnik trenera do konkretnego terminu: slot, wydarzenie albo wpis w kalendarzu
-- treningowym zawodnika. Notatkę widzi WYŁĄCZNIE jej autor — nie klient, nie zawodnik i nie
-- drugi admin. Stąd author_id w kluczu unikalnym, a nie sam cel: to nie jest wspólna adnotacja
-- back office, tylko notatnik jednej osoby.
--
-- Jedna tabela na trzy cele, nie trzy tabele: trzy byłyby trzecią i czwartą kopią tego samego
-- serwisu, czyli dokładnie bliźniactwem slot/event, które w tym projekcie już kosztowało bugi.
-- Wzorzec XOR jest w repo (reserved_seats, training_attachments).
--
-- ⚠️ FK z ON DELETE CASCADE są tu MECHANIZMEM, nie ozdobą, i dlatego cel NIE jest zapisany
-- jako para (target_type, target_id) — taki kształt dałby jeden upsert zamiast trzech, ale
-- zostawiałby po skasowanym slocie/wydarzeniu/treningu wiersz z cudzym tekstem, którego nic
-- już nie sprząta i którego nikt nie zobaczy, żeby usunąć. Notatka ma umierać razem z tym,
-- czego dotyczy — i razem z kontem autora.
CREATE TABLE admin_private_notes (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id    UUID NOT NULL REFERENCES users(id)             ON DELETE CASCADE,
    time_slot_id UUID          REFERENCES time_slots(id)         ON DELETE CASCADE,
    event_id     UUID          REFERENCES events(id)             ON DELETE CASCADE,
    training_id  UUID          REFERENCES personal_trainings(id) ON DELETE CASCADE,
    body         VARCHAR(4000) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Dokładnie jeden cel. Wydarzenie niesie JEDNĄ notatkę niezależnie od tego, ile dni trwa,
    -- więc jego notatka wisi na evencie, nigdy na dobowych slotach, które zakłada mu pierwszy
    -- zapis rezerwacji (serwis odrzuca slot z event_id — tu widać tylko, że kolumny są rozłączne).
    CONSTRAINT chk_admin_private_notes_single_target CHECK (
        (time_slot_id IS NOT NULL)::int
      + (event_id     IS NOT NULL)::int
      + (training_id  IS NOT NULL)::int = 1),

    -- Pusta notatka to notatka usunięta, nie notatka pusta: inaczej wyczyszczenie pola
    -- zostawiałoby wiersz, który w UI wygląda jak "napisano tu coś" i otwiera pusty podgląd.
    CONSTRAINT chk_admin_private_notes_body_not_blank CHECK (btrim(body) <> '')
);

-- Jedna notatka na parę (autor, cel). Indeksy są PARTIAL, bo w wierszu dwie z trzech kolumn
-- celu są NULL, a NULL-e nie kolidują ze sobą w zwykłym UNIQUE — bez predykatu indeks
-- przepuściłby dowolnie wiele notatek "bez slotu". Predykat jest też warunkiem, żeby
-- ON CONFLICT (...) WHERE ... mógł ten indeks wskazać.
CREATE UNIQUE INDEX uq_admin_private_notes_slot
    ON admin_private_notes (author_id, time_slot_id) WHERE time_slot_id IS NOT NULL;
CREATE UNIQUE INDEX uq_admin_private_notes_event
    ON admin_private_notes (author_id, event_id)     WHERE event_id     IS NOT NULL;
CREATE UNIQUE INDEX uq_admin_private_notes_training
    ON admin_private_notes (author_id, training_id)  WHERE training_id  IS NOT NULL;
