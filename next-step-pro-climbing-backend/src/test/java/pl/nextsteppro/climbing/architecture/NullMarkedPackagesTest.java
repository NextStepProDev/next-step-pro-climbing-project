package pl.nextsteppro.climbing.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every package holding code must carry {@code @NullMarked} in its own {@code package-info.java}.
 *
 * <p>The trap this gate closes is that the annotation does not cascade. JSpecify says it outright:
 * "This annotation has no effect on subpackages." So the {@code @NullMarked} sitting on the root
 * {@code pl.nextsteppro.climbing} covers exactly the one class in that package and nothing below
 * it — which looked like project-wide coverage for a long time while 27 packages, the oldest and
 * busiest among them, sat outside null-marked scope.
 *
 * <p>Being outside that scope is not merely "less documentation". An unannotated type there has
 * <i>unspecified</i> nullness rather than non-null, so the {@code @Nullable} marks those packages
 * do carry only tell half the story: they say where null is allowed without ever saying that
 * everywhere else it is not. Half a contract reads like a whole one, which is the expensive part.
 *
 * <p>Nothing in the build enforces nullness itself — there is no NullAway and javac ignores these
 * annotations entirely — so the marking is what makes an IDE's null analysis usable rather than a
 * wall of false alarms. That is precisely the kind of convention that erodes silently: it lives in
 * CLAUDE.md, applies to whichever package someone remembered, and nothing ever goes red.
 */
class NullMarkedPackagesTest {

    private static final String PACKAGE_INFO = "package-info.java";
    private static final String ROOT_PACKAGE = "pl.nextsteppro.climbing";

    @Test
    void shouldMarkEveryPackageThatHoldsCode() {
        Map<Path, Boolean> packagesWithCode = new LinkedHashMap<>();
        for (Path file : SourceFiles.mainJavaFiles()) {
            boolean isPackageInfo = PACKAGE_INFO.equals(file.getFileName().toString());
            packagesWithCode.merge(file.getParent(), !isPackageInfo, Boolean::logicalOr);
        }

        List<String> offenders = new ArrayList<>();
        packagesWithCode.forEach((directory, holdsCode) -> {
            if (!holdsCode) {
                return;
            }
            Path packageInfo = directory.resolve(PACKAGE_INFO);
            if (!packageInfo.toFile().isFile()) {
                offenders.add("%s  (no %s)".formatted(directory, PACKAGE_INFO));
                return;
            }
            if (!SourceFiles.readWithoutComments(packageInfo).contains("@NullMarked")) {
                offenders.add("%s  (%s carries no @NullMarked)".formatted(directory, PACKAGE_INFO));
            }
        });

        assertTrue(offenders.isEmpty(), """
            Found %d package(s) holding code outside null-marked scope.

            @NullMarked does not reach subpackages, so each package needs its own \
            package-info.java. Copy an existing one and change the package declaration:

                @NullMarked
                package %s.<your.package>;

                import org.jspecify.annotations.NullMarked;

            %s""".formatted(offenders.size(), ROOT_PACKAGE, String.join("\n", offenders)));
    }

    /**
     * A package-info copied from a sibling and left with the sibling's package declaration does not
     * fail compilation — it silently marks a package that already had one and leaves its own bare.
     * The gate above would stay green, so the cheap check belongs right next to it.
     */
    @Test
    void shouldDeclareThePackageThatMatchesTheDirectory() {
        List<String> offenders = new ArrayList<>();

        for (Path file : SourceFiles.mainJavaFiles()) {
            if (!PACKAGE_INFO.equals(file.getFileName().toString())) {
                continue;
            }
            Path relative = Path.of("src/main/java").relativize(file.getParent());
            String expected = relative.toString().replace('/', '.');
            String declared = SourceFiles.readWithoutComments(file)
                .lines()
                .filter(line -> line.startsWith("package "))
                .findFirst()
                .orElse("")
                .replace("package ", "")
                .replace(";", "")
                .trim();
            if (!expected.equals(declared)) {
                offenders.add("%s  declares '%s', expected '%s'".formatted(file, declared, expected));
            }
        }

        assertTrue(offenders.isEmpty(), """
            Found %d package-info.java file(s) declaring a package other than their own directory. \
            Such a file marks somebody else's package and leaves its own unmarked.

            %s""".formatted(offenders.size(), String.join("\n", offenders)));
    }
}
