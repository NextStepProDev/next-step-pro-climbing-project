package pl.nextsteppro.climbing.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
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
 * <p>To see this gate red, either shape works and both were checked when it was rewritten: inject
 * {@code SettlementRepository} into {@code CalendarService}, or — the one an import search would
 * miss entirely — put {@code "SELECT COUNT(s) FROM Settlement s"} in a repository of another
 * package, where naming the entity needs no import at all.
 */
class SettlementIsolationTest {

    private static final String SETTLEMENT_PACKAGE = "pl.nextsteppro.climbing.domain.settlement";
    private static final String SETTLEMENT_API_PACKAGE = "pl.nextsteppro.climbing.api.admin.settlement";
    private static final List<String> PACKAGE_NAMES =
        List.of(SETTLEMENT_PACKAGE, SETTLEMENT_API_PACKAGE);

    /**
     * Matches a top-level public declaration: `public record PayerBalance(`, `public class …`.
     *
     * <p>⚠️ Any modifiers between {@code public} and the keyword, not a fixed pair. Spelling out
     * {@code final|abstract} missed {@code sealed} and {@code non-sealed}, and a type this pattern
     * misses is a type this gate silently stops guarding — the exact failure the derivation exists
     * to prevent. Leading whitespace is allowed for the same reason: stripping a block comment that
     * sat before the declaration leaves the line indented.
     *
     * <p>Anchored to the line so a nested or local class cannot enter the list; those are not
     * reachable from another package anyway.
     */
    private static final Pattern PUBLIC_TYPE = Pattern.compile(
        "(?m)^\\s*public\\s+(?:[\\w-]+\\s+)*(?:class|interface|enum|record)\\s+(\\w+)");

    /**
     * Every public type the two money packages declare, read from the sources at run time.
     *
     * <p>⚠️ Derived rather than written down, and that is the whole point of this version. The list
     * used to be maintained by hand, and the gate's own note admitted the consequence: a type nobody
     * remembered to add was protected by nothing. That was not theoretical — putting a
     * {@code public record PayerDigest(UUID payerId, BigDecimal owed)} in {@code api/admin/settlement}
     * and importing it into {@code CalendarService}, which serves the anonymous cached calendar, left
     * this gate GREEN. Two public records had already been missed once before and added after the
     * fact. Reading the packages means the next one is covered on the day it is written.
     *
     * <p>Package-private types need no entry: they cannot be named from another package at all, so
     * the compiler is already the gate for them.
     *
     * <p>⚠️ If this gate ever lights up across half the codebase at once, read the name it reports
     * before hunting for a leak: a public type in these packages called something ordinary — a
     * {@code Period}, a {@code Line} — turns every file mentioning that word into an offender. The
     * answer is to make it package-private, which the DTOs here already are, rather than to loosen
     * the scan.
     *
     * <p>Sorted longest first so an offending file is reported against the type it actually names —
     * {@code \bSettlement\b} does not match inside {@code SettlementRepository}, but reporting the
     * wrong one would send the reader to the wrong file.
     */
    private static List<String> settlementTypes() {
        List<String> types = new ArrayList<>();
        for (Path file : SourceFiles.mainJavaFiles()) {
            if (!isGuardedPackage(file)) continue;
            Matcher matcher = PUBLIC_TYPE.matcher(SourceFiles.readWithoutComments(file));
            while (matcher.find()) {
                types.add(matcher.group(1));
            }
        }
        types.sort(Comparator.comparingInt(String::length).reversed());
        return types;
    }

    private static boolean isGuardedPackage(Path file) {
        String path = file.toString().replace('\\', '/');
        return ALLOWED_PACKAGE_PATHS.stream().anyMatch(path::contains);
    }

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
        List<String> types = settlementTypes();

        for (Path file : SourceFiles.mainJavaFiles()) {
            String path = file.toString().replace('\\', '/');
            if (isGuardedPackage(file)) continue;
            if (path.endsWith(SCHEDULER_EXCEPTION)) continue;

            String source = SourceFiles.readWithoutComments(file);

            // Either package in any import shape. `import ...domain.settlement.*;` is the local
            // house style — several services import their own domain package that way — so keying
            // only off fully qualified type names would leave the most likely bypass wide open. The
            // api package is named here too: its DTOs are package-private today, but a wildcard
            // import of it is the same gesture and deserves the same answer.
            String importedPackage = PACKAGE_NAMES.stream().filter(source::contains).findFirst().orElse(null);
            if (importedPackage != null) {
                offenders.add(path + " imports " + importedPackage);
                continue;
            }
            for (String type : types) {
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

    /**
     * ⚠️ The derivation's own guard, and it matters more than it looks.
     *
     * <p>A list read from the sources fails in a way a written one cannot: a regex that stops
     * matching returns an EMPTY list, every outside file then references none of nothing, and the
     * gate goes green while guarding literally zero types. Green would mean "the scan broke", which
     * is indistinguishable from "the code is clean" unless something checks the scan itself.
     *
     * <p>So it names the types it must find. Not a restatement of the derivation — these are the
     * shapes carrying what a named person was charged, holds or is covered by, and each of them
     * disappearing from the list would silently unguard the thing this whole class exists for.
     */
    @Test
    @DisplayName("shouldDeriveTheTypesItGuardsInsteadOfGuardingNothing")
    void shouldDeriveTheTypesItGuardsInsteadOfGuardingNothing() {
        List<String> types = settlementTypes();

        assertTrue(types.size() >= 17,
            "The scan found only " + types.size() + " public types in the money packages. A broken "
                + "pattern returns an empty list and this gate then passes by guarding nothing: "
                + types);
        for (String mustFind : List.of("Settlement", "SettlementRow", "PayerBalance",
                "SessionCoverage", "Subscription", "Payout", "AdminSettlementService")) {
            assertTrue(types.contains(mustFind),
                "The scan no longer finds " + mustFind + ", so nothing stops another package from "
                    + "reading it. Found: " + types);
        }
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
