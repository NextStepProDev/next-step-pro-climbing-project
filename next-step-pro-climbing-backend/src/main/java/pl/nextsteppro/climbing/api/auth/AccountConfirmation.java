package pl.nextsteppro.climbing.api.auth;

import org.springframework.stereotype.Component;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogService;
import pl.nextsteppro.climbing.domain.user.User;

/**
 * The single way an account becomes confirmed.
 *
 * <p>Confirming is two writes that have to happen together: {@link User#markEmailVerified()}
 * stamps {@code email_verified_at}, which is what the admin panel badge counts, and an activity
 * entry is what the admin timeline shows. Stamping the column without the entry leaves the dot
 * claiming a new account that the timeline cannot account for, and nothing fails — so the guard
 * is this component rather than a review checklist. Same reasoning as
 * {@link VerificationLinkIssuer}: a handful of lines that must agree, in one place instead of
 * three.
 *
 * <p>Three paths reach it today: the e-mail confirmation link, an OAuth sign-up the provider
 * vouched for, and an OAuth login linking to an account that had never confirmed its address.
 * Public rather than package-private because the last two live in {@code config}.
 */
@Component
public class AccountConfirmation {

    /**
     * How the address came to be trusted. Rendered as-is next to the entry in the admin panel, so
     * it stays short — the user's name and e-mail are already their own columns there.
     */
    public enum ConfirmationSource {
        /** The user clicked the link we mailed them. */
        EMAIL_LINK("e-mail"),
        /** An OAuth provider stated it had verified the address ({@code email_verified}). */
        OAUTH("Google");

        private final String label;

        ConfirmationSource(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final ActivityLogService activityLogService;

    public AccountConfirmation(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    /**
     * Marks {@code user} confirmed and records it for the admin panel.
     *
     * <p>Does not save the user: every caller already persists it, either explicitly or through a
     * managed entity in an open transaction.
     */
    public void confirm(User user, ConfirmationSource source) {
        user.markEmailVerified();
        activityLogService.logAccountConfirmed(user, source.label());
    }
}
