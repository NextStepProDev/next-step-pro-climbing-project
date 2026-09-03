package pl.nextsteppro.climbing.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What people paid must stay unreadable from anywhere that builds a shared response.
 *
 * <p>Same gate as {@code PrivateNoteIsolationTest}, and for the same reason with a worse outcome.
 * The risk is not a missing permission check — it is a helpful field. {@code TimeSlotDto} and
 * {@code DaySummaryDto} are served to anonymous visitors and cached under
 * {@code calendarMonth/Week/Day} whenever {@code userId == null}; adding an {@code amount} or a
 * {@code settled} flag to either would compile, would look like a convenience, and would publish
 * what a named person was charged to everybody who opens the calendar.
 *
 * <p>Rather than asserting the absence of a field name — which drifts, and which cannot cover a DTO
 * nobody has written yet — this pins the reachability: the settlement types are visible only inside
 * their own two packages. A service that cannot read an amount cannot leak one.
 *
 * <p>To see this gate red: inject {@code SettlementRepository} into {@code CalendarService}.
 */
class SettlementIsolationTest {

    private static final String SETTLEMENT_PACKAGE = "pl.nextsteppro.climbing.domain.settlement";

    /**
     * Longest first, so an offending file is reported against the type it actually names —
     * {@code \bSettlement\b} does not match inside {@code SettlementRepository}, but reporting the
     * wrong one would send the reader to the wrong file.
     */
    private static final List<String> SETTLEMENT_TYPES = List.of(
        // ⚠️ The services belong here even though they live in api/admin/settlement rather than the
        // domain package. `\bSettlement\b` does NOT match inside `AdminSettlementService` — there is
        // no word boundary in the middle of an identifier — so without naming them explicitly the
        // whole service layer was reachable from anywhere with this gate still green, which is the
        // opposite of what this class's own javadoc promises. Two of them are package-private as
        // well, but AdminSettlementService and AdminSubscriptionService cannot be.
        "AdminSettlementStatsService", "AdminSubscriptionService", "AdminSettlementService",
        "AdminPayoutService",
        "SettlementRepository", "SettlementRow", "SessionPayoutRepository", "SessionPayoutRow",
        "SubscriptionRepository", "PayoutSourceRepository", "PayoutRepository", "PayerLastAmount", "UnpricedPayer",
        // ⚠️ Public records carrying what one named person owes, holds, or is covered by — every bit
        // as leakable as SettlementRow and, being public, reachable by a plain import from anywhere.
        // They were missed when this list was written, which is the failure mode this gate has: a
        // type nobody remembered to name is protected by nothing at all.
        "PayerBalance", "SessionCoverage",
        "SessionPayout", "PayoutSource", "PayoutRow", "Subscription", "Settlement", "Payout");

    /**
     * The one file outside those packages allowed to name a settlement type, and only because the
     * alternative is worse: a {@code @Scheduled} method on the service's own class would bypass the
     * AOP proxy and run without a transaction. The scheduler holds nothing but a reference and calls
     * one method returning a count, so no amount passes through it.
     */
    private static final String SCHEDULER_EXCEPTION =
        "infrastructure/scheduler/SubscriptionBillingScheduler.java";

    /**
     * The only two packages allowed to touch money: the entity's own home, and the admin API that
     * serves it. Widening this list is a decision about who can read what clients were charged —
     * make it deliberately, not by adding an import.
     */
    private static final Set<String> ALLOWED_PACKAGE_PATHS = Set.of(
        "pl/nextsteppro/climbing/domain/settlement",
        "pl/nextsteppro/climbing/api/admin/settlement"
    );

    @Test
    @DisplayName("shouldKeepSettlementsUnreachableFromEveryOtherPackage")
    void shouldKeepSettlementsUnreachableFromEveryOtherPackage() {
        List<String> offenders = new ArrayList<>();

        for (Path file : SourceFiles.mainJavaFiles()) {
            String path = file.toString().replace('\\', '/');
            if (ALLOWED_PACKAGE_PATHS.stream().anyMatch(path::contains)) continue;
            if (path.endsWith(SCHEDULER_EXCEPTION)) continue;

            String source = SourceFiles.readWithoutComments(file);

            // The package in any import shape. `import ...domain.settlement.*;` is the local house
            // style — several services import their own domain package that way — so keying only
            // off fully qualified type names would leave the most likely bypass wide open.
            if (source.contains(SETTLEMENT_PACKAGE)) {
                offenders.add(path + " imports " + SETTLEMENT_PACKAGE);
                continue;
            }
            for (String type : SETTLEMENT_TYPES) {
                // Word boundaries, not `type + " "`: a wildcard import followed by
                // `Settlement.MAX_AMOUNT` or `List<SettlementRow>` puts a `.` or a `>` after the
                // name, and a looser check waves both through.
                if (Pattern.compile("\\b" + type + "\\b").matcher(source).find()) {
                    offenders.add(path + " references " + type);
                    break;
                }
            }
        }

        assertEquals(List.of(), offenders,
            "An amount must not be readable outside domain/settlement and api/admin/settlement. "
                + "Every shape describing a session is shared with clients or the anonymous "
                + "calendar cache, so a settlement reachable from one of those services is one "
                + "field away from publishing what somebody paid.");
    }

    @Test
    @DisplayName("shouldFindTheSettlementSourcesItClaimsToGuard")
    void shouldFindTheSettlementSourcesItClaimsToGuard() {
        // Self-check: a gate scanning the wrong tree passes silently and proves nothing.
        long guarded = SourceFiles.mainJavaFiles().stream()
            .map(p -> p.toString().replace('\\', '/'))
            .filter(p -> ALLOWED_PACKAGE_PATHS.stream().anyMatch(p::contains))
            .count();

        assertTrue(guarded >= 18,
            "Expected both money models: settlements (entity, repository, projections) and bulk "
                + "payouts (source, assignment, payout, their repositories and projections), plus "
                + "the controller, services, DTOs and the two path enums — found " + guarded);
    }
}
