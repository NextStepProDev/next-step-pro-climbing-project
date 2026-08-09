package pl.nextsteppro.climbing.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A message key present in the default bundle but missing from {@code _en}/{@code _es} does not
 * fail anything at startup — the user just gets the Polish string, or the raw key, in the middle
 * of an otherwise translated email. Cheap to check, invisible otherwise.
 */
class MessageBundleParityTest {

    private static final String BASE = "messages.properties";
    private static final Set<String> TRANSLATIONS = Set.of("messages_en.properties", "messages_es.properties");

    @Test
    void shouldKeepEveryMessageKeyInEveryLanguageBundle() {
        Set<String> baseKeys = keysOf(BASE);
        assertTrue(baseKeys.size() > 100,
            "Parsed only " + baseKeys.size() + " keys from " + BASE + " — the parser broke, not the bundle");

        StringBuilder problems = new StringBuilder();
        for (String bundle : new TreeSet<>(TRANSLATIONS)) {
            Set<String> keys = keysOf(bundle);

            Set<String> missing = new TreeSet<>(baseKeys);
            missing.removeAll(keys);
            Set<String> extra = new TreeSet<>(keys);
            extra.removeAll(baseKeys);

            if (!missing.isEmpty()) {
                problems.append("%n%s is missing %d key(s): %s".formatted(bundle, missing.size(), missing));
            }
            if (!extra.isEmpty()) {
                // An extra key is dead weight, but more often it is a typo of a real one.
                problems.append("%n%s has %d key(s) absent from the base bundle: %s"
                    .formatted(bundle, extra.size(), extra));
            }
        }

        assertTrue(problems.isEmpty(), "Message bundles are out of sync:" + problems);
    }

    private static Set<String> keysOf(String bundle) {
        Path path = SourceFiles.mainResource(bundle);
        Set<String> keys = new LinkedHashSet<>();
        try {
            for (String raw : Files.readAllLines(path)) {
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("!") || !line.contains("=")) {
                    continue;
                }
                keys.add(line.substring(0, line.indexOf('=')).strip());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path.toAbsolutePath(), e);
        }
        return keys;
    }
}
