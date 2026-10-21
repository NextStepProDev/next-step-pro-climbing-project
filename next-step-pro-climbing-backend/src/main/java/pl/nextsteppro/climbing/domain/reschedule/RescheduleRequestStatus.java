package pl.nextsteppro.climbing.domain.reschedule;

public enum RescheduleRequestStatus {
    /** Awaiting the coach's answer. The requester's booking stands unchanged meanwhile. */
    PENDING,
    /** The coach accepted — the whole slot/event was moved to the requested term. */
    ACCEPTED,
    /** The coach refused; the booking stays where it was. */
    REJECTED,
    /** The original term ended before anyone answered (scheduler). */
    EXPIRED,
    /** The requester took it back, or their booking was cancelled. */
    WITHDRAWN
}
