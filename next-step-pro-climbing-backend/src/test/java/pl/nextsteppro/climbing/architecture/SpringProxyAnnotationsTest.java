package pl.nextsteppro.climbing.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring's proxy-based AOP only advises {@code public} methods. A {@code @Transactional} or
 * {@code @CacheEvict} on a private or package-private method compiles, reads as correct in review,
 * and does absolutely nothing — the worst possible failure mode, because the code documents a
 * guarantee it does not provide.
 *
 * <p>Self-invocation has the same effect and cannot be detected this cheaply; this gate covers the
 * half that can be.
 */
class SpringProxyAnnotationsTest {

    private static final List<String> PROXIED_ANNOTATIONS =
        List.of("@Transactional", "@Cacheable", "@CacheEvict", "@Caching", "@Async", "@PreAuthorize");

    /** A method declaration: modifiers then `name(`. Excludes control flow like `if (`. */
    private static final Pattern METHOD_DECLARATION = Pattern.compile(
        "^\\s*(?<mods>(?:(?:public|protected|private|static|final|synchronized|abstract|default|native)\\s+)*)"
            + "(?!return\\b|new\\b|if\\b|for\\b|while\\b|switch\\b|catch\\b)"
            + "[\\w<>\\[\\],.?\\s]+\\s+(?<name>\\w+)\\s*\\(");

    @Test
    void shouldOnlyPutProxiedAnnotationsOnPublicMethods() {
        List<String> offenders = new ArrayList<>();

        for (Path file : SourceFiles.mainJavaFiles()) {
            String[] lines = SourceFiles.readWithoutComments(file).split("\n", -1);

            for (int i = 0; i < lines.length; i++) {
                String annotation = proxiedAnnotationOn(lines[i]);
                if (annotation == null) {
                    continue;
                }
                MethodTarget target = nextMethodDeclaration(lines, i + 1);
                if (target == null || target.isInterfaceOrAbstract) {
                    continue; // annotation on a type, a field, or an interface method
                }
                if (!target.isPublic) {
                    offenders.add("%s:%d  %s on non-public method %s()"
                        .formatted(file, target.line, annotation, target.name));
                }
            }
        }

        assertTrue(offenders.isEmpty(), """
            Spring proxies only advise public methods, so each of these annotations is inert while \
            looking active:

            %s

            Either make the method public, or move the annotation to the public entry point that \
            calls it (see AdminService.createDefaultSlotsForEvent for the documented pattern where \
            the caller evicts on a private method's behalf).""".formatted(String.join("\n", offenders)));
    }

    private static String proxiedAnnotationOn(String line) {
        String trimmed = line.strip();
        for (String annotation : PROXIED_ANNOTATIONS) {
            if (trimmed.equals(annotation) || trimmed.startsWith(annotation + "(")) {
                return annotation;
            }
        }
        return null;
    }

    /** Walks past further annotations to the declaration the annotation actually applies to. */
    private static MethodTarget nextMethodDeclaration(String[] lines, int from) {
        for (int i = from; i < lines.length && i < from + 12; i++) {
            String line = lines[i];
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("@") || trimmed.startsWith(")")) {
                continue;
            }
            // Annotation sat on a class/interface/record, not a method.
            if (trimmed.matches(".*\\b(class|interface|enum|record)\\s+\\w+.*")) {
                return null;
            }
            var matcher = METHOD_DECLARATION.matcher(line);
            if (!matcher.find()) {
                return null; // field or something we do not understand — do not guess
            }
            String mods = matcher.group("mods");
            boolean bodyless = trimmed.endsWith(";");
            return new MethodTarget(
                matcher.group("name"), i + 1, mods.contains("public"), bodyless || mods.contains("abstract"));
        }
        return null;
    }

    private record MethodTarget(String name, int line, boolean isPublic, boolean isInterfaceOrAbstract) {
    }
}
