package pl.nextsteppro.climbing.api.admin.userexport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every column that ties a row to a person is either in the personal-data export (GDPR art. 15/20)
 * or deliberately left out — written down here, with the reason.
 *
 * <p>CLAUDE.md said it plainly: "a new section of user data = add it to the export, otherwise the
 * answer to a request is incomplete without any symptom". That was a rule for people to remember;
 * this reads the schema, so a new table referencing {@code users} fails the build until somebody
 * decides which list it belongs on.
 */
class UserDataExportCoverageTest extends BaseIntegrationTest {

    /** In {@code UserDataExportService}, or in the money export the browser fetches beside it. */
    private static final Set<String> EXPORTED = Set.of(
        "activity_logs.user_id",
        "athlete_goals.athlete_id",
        "athlete_weights.athlete_id",
        "climbing_ascents.athlete_id",
        "event_waitlist.user_id",
        "newsletter_consent_log.user_id",
        "personal_trainings.athlete_id",
        "reservations.user_id",
        "reserved_seats.user_id",
        "training_comments.athlete_id",
        "training_requests.user_id",
        "user_news_stars.user_id",
        "waitlist.user_id",
        // money export (/admin/settlements/payers/user/{id}/export)
        "payments.user_id",
        "settlements.user_id",
        "subscriptions.user_id",
        "subscription_skipped_months.user_id"   // as SubscriptionDto.skippedMonths
    );

    private static final Map<String, String> LEFT_OUT = Map.ofEntries(
        entry("auth_tokens.user_id", "hashes of credentials — secrets are never exported"),
        entry("admin_private_notes.author_id", "the admin's own notebook; never leaves its package (CLAUDE.md)"),
        entry("activity_logs.subject_user_id", "admin-side entries about this person; ADMIN_* is left out of the export on purpose — the one that matters to them (cancelled by admin) is logged under their own id"),
        entry("session_payouts.user_id", "bookkeeping mark derived from the subscription and the booking, both exported"),
        entry("training_calendar_reads.athlete_id", "read markers (timestamps of opening a calendar) — technical state"),
        entry("training_calendar_reads.user_id", "read markers — technical state"),
        entry("training_comments.author_id", "the same rows as training_comments.athlete_id, already exported per athlete"),
        entry("training_deletions.athlete_id", "60-day alert log of future entries that were removed; their content was the plan itself")
    );

    @Autowired private JdbcTemplate jdbc;

    @Test
    void shouldDecideForEveryColumnThatPointsAtAUser() {
        List<String> columns = jdbc.queryForList("""
            SELECT tc.table_name || '.' || kcu.column_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name
            JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = tc.constraint_name
            WHERE tc.constraint_type = 'FOREIGN KEY' AND ccu.table_name = 'users' AND tc.table_schema = 'public'
            """, String.class);

        Set<String> undecided = new TreeSet<>(columns);
        undecided.removeAll(EXPORTED);
        undecided.removeAll(LEFT_OUT.keySet());
        assertEquals(Set.of(), undecided,
            "Put each column in EXPORTED (and in the export) or in LEFT_OUT with a reason");

        Set<String> stale = new TreeSet<>(EXPORTED);
        stale.addAll(LEFT_OUT.keySet());
        stale.removeAll(columns);
        assertEquals(Set.of(), stale, "A list entry for a column that no longer exists excuses whatever takes its name");
    }
}
