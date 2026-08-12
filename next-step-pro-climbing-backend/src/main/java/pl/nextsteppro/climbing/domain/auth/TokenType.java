package pl.nextsteppro.climbing.domain.auth;

public enum TokenType {
    EMAIL_VERIFICATION,
    PASSWORD_RESET,
    REFRESH_TOKEN
    // NEWSLETTER_UNSUBSCRIBE is gone: the unsubscribe link now rides a permanent column on the
    // user (V81), because a rotating token here killed every link in every email already sent.
}
