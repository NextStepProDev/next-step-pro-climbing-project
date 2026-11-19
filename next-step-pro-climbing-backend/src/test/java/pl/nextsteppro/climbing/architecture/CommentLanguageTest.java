package pl.nextsteppro.climbing.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Comments are written in English — in the backend, the workflows and the hub.
 *
 * <p>Until 2026-11-19 nothing said so, and the repo drifted: Polish comments sat in the Gradle
 * build, the version catalog, the Dockerfile, the compose files, every CI workflow and the load
 * tests, next to English ones in the same files. A rule in CLAUDE.md alone is a rule someone has to
 * remember on every edit; this gate makes the drift fail the build instead.
 *
 * <p>Detection is a heuristic on each comment line: quoted text ({@code "…"}, backticks,
 * {@code {@code …}}) is removed first, because English comments legitimately quote Polish UI
 * strings. A line is flagged when what remains has no common English word and either contains a
 * Polish letter or at least two common Polish words. The frontend has the same gate
 * ({@code src/__architecture__/commentLanguage.test.ts}) with the same rule.
 *
 * <p>Flyway migrations up to {@link #LAST_POLISH_MIGRATION} are skipped on purpose: they are applied
 * on production, and changing even a comment changes the checksum, so Flyway refuses to start.
 * Every later migration is checked like any other file.
 */
class CommentLanguageTest {

    /** Module dir is the working directory (see {@link SourceFiles}). */
    private static final List<Path> ROOTS = List.of(
        Path.of("."),
        Path.of("../.github"),
        Path.of("../next-step-pro-climbing-hub"));

    private static final Set<String> SKIPPED_DIRS = Set.of(
        "build", ".gradle", "node_modules", "bin", "out", ".idea");

    /** The newest migration written before this gate; it and everything older is frozen. */
    private static final int LAST_POLISH_MIGRATION = 106;
    private static final Pattern MIGRATION = Pattern.compile("V(\\d+)__.*\\.sql");

    private static final Set<String> POLISH = Set.of((
        "w z na do i się że ze jest nie dla przy oraz po od jako ten tego już też lub albo bez przez"
            + " gdzie kiedy tak tylko gdy więc bo ale czy jak żeby musi być można będzie który która"
            + " które jeśli tutaj tym tej są brak").split(" "));

    private static final Set<String> ENGLISH = Set.of(
        "the an and or of in on at by for with from as is are was be it this that not than but when if has have".split(" "));

    private static final Pattern POLISH_LETTER = Pattern.compile("[ąćęłńóśźżĄĆĘŁŃÓŚŹŻ]");
    private static final Pattern QUOTED =
        Pattern.compile("\\{@code[^}]*}|\"[^\"]*\"|„[^”\"]*[”\"]|“[^”]*”|`[^`]*`|«[^»]*»");
    private static final Pattern WORD = Pattern.compile(
        "(?<![\\w-])[a-ząćęłńóśźż]+(?![\\w-])", Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern BLOCK_OPEN = Pattern.compile("(^|[\\s{(])/\\*");
    private static final Pattern LINE_COMMENT = Pattern.compile("(^|\\s)//(.*)$");
    private static final Pattern HASH_COMMENT = Pattern.compile("(^\\s*#|\\s#(?=\\s|$))(.*)$");
    private static final Pattern SQL_COMMENT = Pattern.compile("(^|\\s)--(.*)$");

    @Test
    void commentsAreInEnglish() {
        List<String> offenders = new ArrayList<>();
        for (Path root : ROOTS) {
            for (Path file : files(root)) {
                Syntax syntax = syntaxOf(file);
                if (syntax == null || isFrozenMigration(file)) continue;
                List<String> lines = SourceFiles.read(file).lines().toList();
                for (Comment c : comments(lines, syntax)) {
                    if (looksPolish(c.text())) {
                        offenders.add(file.normalize() + ":" + c.line() + ": " + c.text().strip());
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(),
            "Comments must be in English (CLAUDE.md, \"Język kodu\"). Polish found in:\n  "
                + String.join("\n  ", offenders));
    }

    @Test
    void ruleCatchesPolishAndIgnoresQuotedUiText() {
        assertTrue(looksPolish(" Wgraj wynik do zakładki Security"));
        assertTrue(looksPolish(" Akcje GitHub w workflowach, tylko raz w miesiącu"));
        assertFalse(looksPolish(" The heading says \"Co zabrać\" on the page"));
        assertFalse(looksPolish(" NFD handles ó/ą/ę, but not ł"));
        assertFalse(looksPolish(" ({@code ó → &oacute;})"));
        assertTrue(isFrozenMigration(Path.of("V106__free_sessions.sql")));
        assertFalse(isFrozenMigration(Path.of("V107__anything.sql")));
    }

    static boolean looksPolish(String text) {
        String unquoted = QUOTED.matcher(text).replaceAll(" ");
        Matcher words = WORD.matcher(unquoted.toLowerCase(Locale.ROOT));
        int polish = 0;
        while (words.find()) {
            String w = words.group();
            if (ENGLISH.contains(w)) return false;
            if (POLISH.contains(w)) polish++;
        }
        return polish >= 2 || POLISH_LETTER.matcher(unquoted).find();
    }

    private enum Syntax { C_LIKE, HASH, SQL }

    private static boolean isFrozenMigration(Path file) {
        Matcher m = MIGRATION.matcher(file.getFileName().toString());
        return m.matches() && Integer.parseInt(m.group(1)) <= LAST_POLISH_MIGRATION;
    }

    private record Comment(int line, String text) {
    }

    private static Syntax syntaxOf(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(".java") || name.endsWith(".js") || name.endsWith(".gradle") || name.endsWith(".kts")) {
            return Syntax.C_LIKE;
        }
        if (name.endsWith(".sql")) {
            return Syntax.SQL;
        }
        if (name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".toml")
            || name.endsWith(".properties") || name.endsWith(".sh") || name.endsWith(".conf")
            || name.startsWith("Dockerfile") || name.equals(".env.example")) {
            return Syntax.HASH;
        }
        return null;
    }

    private static List<Comment> comments(List<String> lines, Syntax syntax) {
        List<Comment> out = new ArrayList<>();
        boolean inBlock = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int n = i + 1;
            if (syntax == Syntax.HASH) {
                Matcher m = HASH_COMMENT.matcher(line);
                if (m.find()) out.add(new Comment(n, m.group(2)));
                continue;
            }
            if (syntax == Syntax.SQL && !inBlock) {
                Matcher sc = SQL_COMMENT.matcher(line);
                if (sc.find()) {
                    out.add(new Comment(n, sc.group(2)));
                    continue;
                }
            }
            if (inBlock) {
                int end = line.indexOf("*/");
                out.add(new Comment(n, end < 0 ? line : line.substring(0, end)));
                inBlock = end < 0;
                continue;
            }
            Matcher open = BLOCK_OPEN.matcher(line);
            if (open.find()) {
                String rest = line.substring(open.end());
                int end = rest.indexOf("*/");
                out.add(new Comment(n, end < 0 ? rest : rest.substring(0, end)));
                inBlock = end < 0;
                continue;
            }
            Matcher lc = LINE_COMMENT.matcher(line);
            if (lc.find()) out.add(new Comment(n, lc.group(2)));
        }
        return out;
    }

    private static List<Path> files(Path root) {
        if (!Files.isDirectory(root)) return List.of();
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                .filter(p -> {
                    for (Path part : root.relativize(p)) {
                        if (SKIPPED_DIRS.contains(part.toString())) return false;
                    }
                    return true;
                })
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot walk " + root.toAbsolutePath(), e);
        }
    }
}
