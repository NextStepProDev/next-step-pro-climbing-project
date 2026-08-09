package pl.nextsteppro.climbing.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The container runs UTC; every date and time in the database is Warsaw local. So a no-arg
 * {@code LocalDate.now()} is wrong by up to two hours, and the damage is invisible outside the
 * 00:00–02:00 window — which is exactly why it survived several reviews in {@code AdminService}
 * (today's reservations dropped out of "upcoming" and doubled up in "past" every night).
 *
 * <p>{@code Instant.now()} is fine: an instant carries no zone, so audit timestamps and expiry
 * checks are correct by construction.
 */
class NoBareNowTest {

    /** LocalDate/LocalTime/LocalDateTime.now() with an empty argument list. */
    private static final Pattern BARE_NOW =
        Pattern.compile("\\b(LocalDate|LocalTime|LocalDateTime)\\.now\\(\\s*\\)");

    /**
     * Deliberate exceptions. Keep this list tiny and justified — an entry here is a decision,
     * and adding one should feel heavier than zoning the call properly.
     */
    private static final Set<String> ALLOWED = Set.of(
        // <lastmod> in the sitemap: cosmetic crawler metadata, never compared to a domain date.
        "SitemapController.java"
    );

    @Test
    void shouldNeverCallNowWithoutAnExplicitZoneInMainSources() {
        List<String> offenders = new ArrayList<>();

        for (Path file : SourceFiles.mainJavaFiles()) {
            if (ALLOWED.contains(file.getFileName().toString())) {
                continue;
            }
            String source = SourceFiles.readWithoutComments(file);
            Matcher matcher = BARE_NOW.matcher(source);
            while (matcher.find()) {
                int line = (int) source.substring(0, matcher.start()).lines().count();
                offenders.add("%s:%d  %s".formatted(file, line, matcher.group()));
            }
        }

        assertTrue(offenders.isEmpty(), """
            Found %d call(s) to now() without a zone. Database dates are Europe/Warsaw local while \
            the container clock is UTC, so these are wrong by up to two hours and misclassify the \
            current day between 00:00 and 02:00 Warsaw.

            Use LocalDate.now(WARSAW) / LocalDateTime.now(WARSAW), or BookingTimeValidator for \
            "has this already happened" questions.

            %s""".formatted(offenders.size(), String.join("\n", offenders)));
    }
}
