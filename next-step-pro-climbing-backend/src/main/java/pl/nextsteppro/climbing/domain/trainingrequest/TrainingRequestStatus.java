package pl.nextsteppro.climbing.domain.trainingrequest;

public enum TrainingRequestStatus {
    /** Awaiting an admin response. */
    PENDING,
    /** Admin created a slot/event from this request (requester invited). */
    ACCEPTED,
    /** Admin marked: contacted the user to arrange a different time. */
    CONTACTED,
    /** Admin rejected the request. */
    REJECTED,
    /** The proposed date passed without a response (scheduler). */
    EXPIRED
}
