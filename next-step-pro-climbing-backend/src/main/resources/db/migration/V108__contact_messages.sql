-- Messages people send from the site ("Write to us" on the contact page, a course page and the
-- calendar) and the coach's replies to them.
--
-- ONE conversation per person, not a thread per topic (decision 2026-11-22): a client asking about
-- a course and later about a date talks to the same coach, so it is one running conversation, and
-- each inbound message carries what it was about as its own context.
--
-- A person is either an account (user_id) or a guest known only by an address (guest_email). A
-- guest who later confirms an account with the same address gets the guest conversation merged
-- into theirs - only after CONFIRMATION, because an address typed at sign-up proves nothing, and
-- merging on registration would let anyone read somebody else's mail by registering their address.
CREATE TABLE contact_conversations (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID REFERENCES users(id) ON DELETE CASCADE,
    guest_email      VARCHAR(255),
    -- The name the guest typed last time. Accounts read the name live from users.
    guest_name       VARCHAR(100),

    -- Two clocks instead of one "last message" column, because they answer different questions:
    -- "is there something the coach has not read" and "is there an answer the person has not seen".
    last_inbound_at  TIMESTAMPTZ NOT NULL,
    last_reply_at    TIMESTAMPTZ,

    -- Shared back-office state, not per admin: "somebody on the team read it" is what the list
    -- needs, and a reply from one admin must not leave the message lit for another.
    staff_read_at    TIMESTAMPTZ,
    user_seen_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_contact_conversations_owner CHECK ((user_id IS NULL) <> (guest_email IS NULL)),
    CONSTRAINT chk_contact_conversations_guest_name CHECK (user_id IS NULL OR guest_name IS NULL)
);

CREATE UNIQUE INDEX uq_contact_conversations_user ON contact_conversations (user_id)
    WHERE user_id IS NOT NULL;
-- Addresses are compared case-insensitively: "Jan@x.pl" and "jan@x.pl" are one mailbox.
CREATE UNIQUE INDEX uq_contact_conversations_guest_email ON contact_conversations (lower(guest_email))
    WHERE guest_email IS NOT NULL;
CREATE INDEX idx_contact_conversations_last_inbound ON contact_conversations (last_inbound_at DESC);

CREATE TABLE contact_messages (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id  UUID NOT NULL REFERENCES contact_conversations(id) ON DELETE CASCADE,
    from_staff       BOOLEAN NOT NULL,
    -- Which admin answered. SET NULL: the reply stays in the conversation when that account goes.
    staff_author_id  UUID REFERENCES users(id) ON DELETE SET NULL,
    -- Plain text, trimmed, NOT escaped: it is rendered as a text node and escaped when it goes into
    -- a mail. No formatting markers - this text travels by mail, which has no renderer for them.
    body             VARCHAR(4000) NOT NULL,

    -- What the message was about. Only inbound messages carry one; a reply is about the conversation.
    context_type     VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    -- SET NULL, and the title is a snapshot: a deleted course must not delete the question about
    -- it, and "about a course" with no name would not tell the coach which one.
    course_id        UUID REFERENCES courses(id) ON DELETE SET NULL,
    course_title     VARCHAR(255),
    context_date     DATE,
    -- The language of the page the message was written on; the reply mail goes out in it.
    language         VARCHAR(5) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_contact_messages_body CHECK (btrim(body) <> ''),
    CONSTRAINT chk_contact_messages_context_type CHECK (context_type IN ('GENERAL', 'COURSE', 'CALENDAR')),
    CONSTRAINT chk_contact_messages_reply_has_no_context CHECK (NOT from_staff OR context_type = 'GENERAL'),
    CONSTRAINT chk_contact_messages_course_context CHECK (
        (context_type = 'COURSE') = (course_title IS NOT NULL)
        AND (context_type = 'COURSE' OR course_id IS NULL)
    ),
    -- A calendar question may name no day: from the week or month view nobody picked one, and
    -- stamping "today" would tell the coach about a day the person never looked at.
    CONSTRAINT chk_contact_messages_calendar_context CHECK (context_type = 'CALENDAR' OR context_date IS NULL)
);

CREATE INDEX idx_contact_messages_conversation ON contact_messages (conversation_id, created_at);
-- The yearly retention sweep deletes by age.
CREATE INDEX idx_contact_messages_created_at ON contact_messages (created_at);
CREATE INDEX idx_contact_messages_course ON contact_messages (course_id) WHERE course_id IS NOT NULL;
