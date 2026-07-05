-- Propozycje terminów treningów składane przez zalogowanych użytkowników.
-- Użytkownik proponuje datę + przedział godzin (opcjonalnie wewnątrz okna dostępności
-- i/lub w kontekście kursu), a admin odpowiada: tworzy slot/wydarzenie z propozycji
-- (created_slot_id / created_event_id + status ACCEPTED), oznacza CONTACTED albo REJECTED.
--
-- window_slot_id: slot-okno dostępności, w którym złożono propozycję (ON DELETE SET NULL —
-- propozycja przeżywa usunięcie okna, traci tylko kontekst).
-- created_slot_id / created_event_id: co powstało z propozycji (link dla admina i użytkownika).
CREATE TABLE training_requests (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    window_slot_id   UUID REFERENCES time_slots(id) ON DELETE SET NULL,
    course_id        UUID REFERENCES courses(id) ON DELETE SET NULL,
    requested_date   DATE NOT NULL,
    start_time       TIME NOT NULL,
    end_time         TIME NOT NULL,
    participants     INT NOT NULL DEFAULT 1,
    comment          VARCHAR(1000),
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    admin_note       VARCHAR(500),
    created_slot_id  UUID REFERENCES time_slots(id) ON DELETE SET NULL,
    created_event_id UUID REFERENCES events(id) ON DELETE SET NULL,
    resolved_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Panel admina filtruje po statusie (badge liczy PENDING), użytkownik listuje swoje.
CREATE INDEX idx_training_requests_status ON training_requests(status);
CREATE INDEX idx_training_requests_user ON training_requests(user_id);

-- Znacznik ręcznej wysyłki maila z zaproszeniem (miejsca "na zaproszenie", V58).
-- NULL = jeszcze nie powiadomiono; admin wyzwala wysyłkę z palca i widzi, kto już dostał maila.
ALTER TABLE reserved_seats ADD COLUMN notified_at TIMESTAMPTZ;
