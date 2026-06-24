package pl.nextsteppro.climbing.domain.event;

public enum EventType {
    COURSE,
    TRAINING,
    WORKSHOP,
    CONTACT_DAY,
    UNAVAILABLE;

    /**
     * Types that never allow enrollment — no reservations and no waitlist.
     * CONTACT_DAY (private session, redirect to phone) and UNAVAILABLE
     * (instructor absence / blocked day) both fall here.
     */
    public boolean blocksEnrollment() {
        return this == CONTACT_DAY || this == UNAVAILABLE;
    }
}
