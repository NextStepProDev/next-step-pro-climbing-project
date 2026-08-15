package pl.nextsteppro.climbing.domain.climbingascent;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns a hand-typed place name into a grouping key.
 *
 * <p>Area and crag are free text — there is no dictionary of Polish crags to pick from — so
 * "Jura Północna", "jura polnocna" and " Jura  Północna " would otherwise be three different
 * places in the area ranking and three different entries in the form's autocomplete. This
 * collapses them: trim, strip diacritics, lowercase, squash runs of whitespace.
 *
 * <p><b>What it does not fix:</b> "Jura" and "Jura Północna" stay two areas, and always will
 * until there is an actual gazetteer to choose from. What keeps that from mattering much is the
 * other half of the design — the form suggests from the athlete's own history, so the second
 * entry for a crag is made by picking, not by typing. That is the difference from
 * {@code events.location}, where the admin retyped the place on every event.
 *
 * <p>Pure and static on purpose (no Spring, no clock — the {@code WeightTrendCalculator} pattern),
 * so the rule has one home and one unit test instead of living in parallel in Java and in SQL.
 */
public final class AscentTextKey {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    private AscentTextKey() {}

    /**
     * Normalizes a place name to its grouping key. Returns an empty string for blank input —
     * rejecting blanks is the service's job, and this class has no opinion on validation.
     *
     * <p>Polish "ł" carries no combining mark in NFD (it is a distinct letter, not l + stroke),
     * so it is replaced explicitly; without that, "Kołoczek" and "Koloczek" would stay apart.
     */
    public static String normalize(String value) {
        String decomposed = Normalizer.normalize(value.trim(), Normalizer.Form.NFD);
        String stripped = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        String delatinized = stripped.replace('ł', 'l').replace('Ł', 'L');
        return WHITESPACE_RUN.matcher(delatinized).replaceAll(" ").toLowerCase(Locale.ROOT);
    }
}
