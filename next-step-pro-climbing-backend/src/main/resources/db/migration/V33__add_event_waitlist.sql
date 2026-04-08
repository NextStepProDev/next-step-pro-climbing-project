-- V33: Lista oczekujących dla wydarzeń (event_waitlist)
-- Analogicznie jak waitlist dla slotów, ale na poziomie wydarzeń/kursów.
-- Gdy ktoś odwoła zapis na wydarzenie, wszyscy oczekujący dostają powiadomienie jednocześnie.
-- Kto pierwszy potwierdzi — dostaje miejsce (race model).

CREATE TABLE event_waitlist (
    id                    UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id               UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_id              UUID         NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    position              INT          NOT NULL,
    status                VARCHAR(25)  NOT NULL DEFAULT 'WAITING',
    offered_at            TIMESTAMP WITH TIME ZONE,
    confirmation_deadline TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_event_waitlist PRIMARY KEY (id),
    CONSTRAINT uq_event_waitlist_user_event UNIQUE (user_id, event_id),
    CONSTRAINT chk_event_waitlist_status CHECK (status IN ('WAITING', 'PENDING_CONFIRMATION', 'EXPIRED'))
);

CREATE INDEX idx_event_waitlist_event_status_position ON event_waitlist(event_id, status, position);
CREATE INDEX idx_event_waitlist_user_id              ON event_waitlist(user_id);
CREATE INDEX idx_event_waitlist_deadline             ON event_waitlist(confirmation_deadline) WHERE status = 'PENDING_CONFIRMATION';
