package pl.nextsteppro.climbing.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads project sources off disk so the architecture gates can assert over the whole tree.
 *
 * <p>These gates exist because an audit is sampling, not proof: a reviewer checks the hypotheses
 * they happened to think of that day, so the same class of defect can survive several reviews.
 * Anything expressible as "no occurrence of X anywhere" belongs here instead — then it is checked
 * on every push and stops being anybody's job to remember.
 *
 * <p>Gradle runs tests with the module directory as the working directory, so these relative
 * paths resolve both locally and in CI.
 */
final class SourceFiles {

    private static final Path MAIN_JAVA = Path.of("src/main/java");
    private static final Path MAIN_RESOURCES = Path.of("src/main/resources");
    private static final Path FRONTEND_SRC = Path.of("../next-step-pro-climbing-frontend/src");

    private SourceFiles() {
    }

    static List<Path> mainJavaFiles() {
        return listRecursively(MAIN_JAVA, ".java");
    }

    static Path mainResource(String name) {
        return MAIN_RESOURCES.resolve(name);
    }

    static Path frontendFile(String relativePath) {
        return FRONTEND_SRC.resolve(relativePath);
    }

    static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path.toAbsolutePath(), e);
        }
    }

    /** Source with block and line comments blanked out, so gates never match commentary. */
    static String readWithoutComments(Path path) {
        return stripComments(read(path));
    }

    static String stripComments(String source) {
        return source
            .replaceAll("(?s)/\\*.*?\\*/", "")
            .replaceAll("(?m)//.*$", "");
    }

    private static List<Path> listRecursively(Path root, String suffix) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(suffix))
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot walk " + root.toAbsolutePath(), e);
        }
    }
}
