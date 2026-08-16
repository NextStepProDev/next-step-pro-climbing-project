package pl.nextsteppro.climbing.architecture;

import org.junit.jupiter.api.Test;
import pl.nextsteppro.climbing.domain.activitylog.ActivityActionType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@code activity_logs.action_type} is a plain VARCHAR with no CHECK, so the backend can ship a
 * new {@link ActivityActionType} without a migration — and the frontend will happily accept it
 * until it tries to render it.
 *
 * <p>This gate deliberately lives on the <em>backend</em> side. Adding an enum constant is a
 * backend change, and the CI path filters mean a backend-only commit runs only {@code ci-backend.yml};
 * the same assertion in a Vitest file would simply not run on the commit that breaks it.
 */
class ActivityActionTypeParityTest {

    /** The map moved out of AdminActivityPanel.tsx when the admin user card's timeline started
     * sharing it — exporting a constant beside a component trips the react-refresh lint rule, and
     * a second copy would be one more place to forget. */
    private static final Path PANEL =
        SourceFiles.frontendFile("components/admin/activityActionConfig.ts");

    /** Keys of the ACTION_CONFIG record literal: two-space indented SCREAMING_SNAKE followed by ':'. */
    private static final Pattern CONFIG_KEY = Pattern.compile("(?m)^\\s{2}([A-Z][A-Z_]{2,}):");

    @Test
    void shouldRenderEveryActivityActionTypeInTheAdminPanel() {
        assumeTrue(Files.exists(PANEL),
            "Frontend sources not checked out next to the backend — skipping cross-module gate");

        String panel = SourceFiles.read(PANEL);
        String configBlock = actionConfigBlock(panel);

        Set<String> mapped = new LinkedHashSet<>();
        Matcher matcher = CONFIG_KEY.matcher(configBlock);
        while (matcher.find()) {
            mapped.add(matcher.group(1));
        }

        Set<String> declared = Arrays.stream(ActivityActionType.values())
            .map(Enum::name)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        assertTrue(mapped.size() >= declared.size() / 2,
            "Parsed only " + mapped.size() + " ACTION_CONFIG keys for " + declared.size()
                + " enum constants — the parser broke rather than the code. Fix the gate.");

        Set<String> missing = new LinkedHashSet<>(declared);
        missing.removeAll(mapped);

        assertTrue(missing.isEmpty(), """
            ActivityActionType values with no ACTION_CONFIG entry: %s

            AdminActivityPanel falls back to a neutral icon, so this no longer white-screens the \
            Activity tab — but an unmapped action still renders anonymously. Adding a value means \
            touching four places: the enum, the union in types/index.ts, ACTION_CONFIG, and the \
            labels in admin.json (pl/en/es).""".formatted(missing));
    }

    /** Extracts the ACTION_CONFIG object literal so trailing code cannot leak into the key scan. */
    private static String actionConfigBlock(String panel) {
        int start = panel.indexOf("const ACTION_CONFIG");
        assertTrue(start >= 0, "ACTION_CONFIG not found in activityActionConfig.ts — gate needs updating");
        int end = panel.indexOf("\n}", start);
        assertTrue(end > start, "Could not find the end of the ACTION_CONFIG literal");
        return panel.substring(start, end);
    }
}
