-- V32: Lista oczekujących (waitlist)
-- Użytkownicy mogą dołączyć do kolejki gdy slot jest pełny.
-- Gdy ktoś odwoła rezerwację, pierwsze miejsce w kolejce dostaje ofertę (PENDING_CONFIRMATION).
-- Użytkownik ma do confirmationDeadline czasu na potwierdzenie.
-- Jeśli nie potwierdzi, oferta przechodzi do następnej osoby w kolejce.

CREATE TABLE waitlist (
    id                    UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id               UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    slot_id               UUID         NOT NULL REFERENCES time_slots(id) ON DELETE CASCADE,
    position              INT          NOT NULL,
    status                VARCHAR(25)  NOT NULL DEFAULT 'WAITING',
    offered_at            TIMESTAMP WITH TIME ZONE,
    confirmation_deadline TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_waitlist PRIMARY KEY (id),
    CONSTRAINT uq_waitlist_user_slot UNIQUE (user_id, slot_id),
    CONSTRAINT chk_waitlist_status CHECK (status IN ('WAITING', 'PENDING_CONFIRMATION', 'EXPIRED'))
);

CREATE INDEX idx_waitlist_slot_status_position ON waitlist(slot_id, status, position);
CREATE INDEX idx_waitlist_user_id              ON waitlist(user_id);
CREATE INDEX idx_waitlist_deadline             ON waitlist(confirmation_deadline) WHERE status = 'PENDING_CONFIRMATION';
