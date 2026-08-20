package pl.nextsteppro.climbing.domain.activitylog;

public enum ActivityActionType {
    // An account became usable: the address was confirmed by e-mail link, or the provider vouched
    // for it during OAuth sign-up. Deliberately not named USER_REGISTERED (registration precedes
    // this by hours on the e-mail path) nor USER_EMAIL_VERIFIED (the Google path verifies nothing
    // of ours). Written by AccountConfirmation, never directly.
    USER_ACCOUNT_CONFIRMED,
    RESERVATION_CREATED,
    RESERVATION_CANCELLED,
    RESERVATION_REACTIVATED,
    EVENT_RESERVATION_CREATED,
    EVENT_RESERVATION_CANCELLED,
    RESERVATION_CANCELLED_BY_ADMIN,
    RESERVATION_UPDATED,
    EVENT_RESERVATION_UPDATED,
    ADMIN_SLOT_CREATED,
    ADMIN_SLOT_UPDATED,
    ADMIN_SLOT_DELETED,
    ADMIN_SLOT_BLOCKED,
    ADMIN_SLOT_UNBLOCKED,
    ADMIN_EVENT_CREATED,
    ADMIN_EVENT_UPDATED,
    ADMIN_EVENT_DELETED,
    ADMIN_USER_MAKE_ADMIN,
    ADMIN_USER_ADMIN_REMOVED,
    ADMIN_USER_DELETED,
    ADMIN_USER_FORCE_LOGOUT,
    ADMIN_USER_ATHLETE_TOGGLED,
    ADMIN_TRAINING_CREATED,
    ADMIN_TRAINING_UPDATED,
    ADMIN_TRAINING_DELETED,
    ADMIN_GOAL_CREATED,
    ADMIN_GOAL_UPDATED,
    ADMIN_GOAL_DELETED,
    ADMIN_GOAL_ACHIEVED,
    // Undo of an AUTOMATIC weight-goal closure (mistyped weigh-in); manual closures are final
    ADMIN_GOAL_REOPENED
}
