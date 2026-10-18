package pl.nextsteppro.climbing.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.nextsteppro.climbing.infrastructure.validation.HttpUrl;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every URL a client can SEND must be an http(s) URL, checked at the door ({@code @HttpUrl}).
 *
 * <p>Found by the 2026-10-18 audit: thumbnail, photo, badge and link URLs were taken as any string
 * and printed later into {@code href}, {@code src} and the public OG stubs — one of which put them
 * into an HTML attribute unescaped. Escaping at each print site is the fragile half (the stub had
 * escaped the title right next to the URL it forgot); this gate keeps the robust half in place for
 * every input added later.
 *
 * <p>"What a client can send" is derived, not listed: every {@code @RequestBody} of every controller,
 * followed into nested records and list elements. A string component named {@code url} or ending in
 * {@code Url} must carry {@code @HttpUrl}. (A first version matched source text by name and missed
 * three inputs — a nested parenthesis in an annotation, and a DTO not named {@code *Request}.)
 */
class UrlFieldsValidatedTest {

    /** URL-shaped inputs that are deliberately validated another way, each with its reason. */
    private static final Set<String> ALLOWED = Set.of(
        // Training material links: AttachmentSupport validates them with its own message, and the
        // same request also carries uploaded-file rows where the field is absent by design.
        "AttachmentRequest.url",
        // Video blocks: the server rewrites the input into an embed URL and refuses anything that is
        // not YouTube or Instagram (VideoEmbedUrls) — stricter than http(s).
        "AddVideoEmbedBlockRequest.url",
        "UpdateVideoEmbedBlockRequest.url"
    );

    @Test
    void shouldValidateEveryUrlAClientCanSend() throws Exception {
        Set<Class<?>> bodies = new HashSet<>();
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (BeanDefinition definition : scanner.findCandidateComponents("pl.nextsteppro.climbing")) {
            for (Method method : Class.forName(definition.getBeanClassName()).getDeclaredMethods()) {
                for (Parameter parameter : method.getParameters()) {
                    if (parameter.isAnnotationPresent(RequestBody.class)) {
                        collect(parameter.getParameterizedType(), bodies);
                    }
                }
            }
        }

        Set<String> offenders = new TreeSet<>();
        int seen = 0;
        Set<String> allowedSeen = new HashSet<>();
        for (Class<?> body : bodies) {
            for (RecordComponent component : body.getRecordComponents()) {
                String name = component.getName();
                if (component.getType() != String.class || !(name.equals("url") || name.endsWith("Url"))) continue;
                seen++;
                String id = body.getSimpleName() + "." + name;
                if (ALLOWED.contains(id)) {
                    allowedSeen.add(id);
                } else if (!hasHttpUrl(component)) {
                    offenders.add(id);
                }
            }
        }

        assertTrue(seen >= 12, "The scan must find the URL inputs it guards (found " + seen + ")");
        assertEquals(ALLOWED, allowedSeen, "A stale allowlist entry quietly excuses whatever takes its name later");
        assertEquals(new TreeSet<String>(), offenders,
            "A URL from a client must be @HttpUrl (or on the allowlist with a reason)");
    }

    /**
     * A constraint on a request record does nothing unless the parameter is {@code @Valid}: Spring
     * validates only what it is told to. 29 bodies had no {@code @Valid}; any of them carrying
     * {@code @Size}, {@code @Min} or {@code @HttpUrl} was checked by nobody, while reading as checked.
     */
    @Test
    void shouldValidateEveryRequestBodyThatDeclaresConstraints() throws Exception {
        Set<String> offenders = new TreeSet<>();
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (BeanDefinition definition : scanner.findCandidateComponents("pl.nextsteppro.climbing")) {
            Class<?> controller = Class.forName(definition.getBeanClassName());
            for (Method method : controller.getDeclaredMethods()) {
                for (Parameter parameter : method.getParameters()) {
                    if (!parameter.isAnnotationPresent(RequestBody.class)) continue;
                    boolean validated = parameter.isAnnotationPresent(jakarta.validation.Valid.class)
                        || parameter.isAnnotationPresent(org.springframework.validation.annotation.Validated.class);
                    Set<Class<?>> records = new HashSet<>();
                    collect(parameter.getParameterizedType(), records);
                    if (!validated && records.stream().anyMatch(UrlFieldsValidatedTest::declaresConstraints)) {
                        offenders.add(controller.getSimpleName() + "." + method.getName());
                    }
                    // Validation cascades into a nested record only through @Valid on the way down
                    // (the component or the container element) — the promo's per-language content,
                    // a Map of records, was never validated though the body was.
                    for (Class<?> record : records) {
                        for (RecordComponent component : record.getRecordComponents()) {
                            if (reachesConstrainedRecord(component.getAnnotatedType())
                                    && !cascades(component.getAnnotatedType())
                                    && !component.isAnnotationPresent(jakarta.validation.Valid.class)) {
                                offenders.add(record.getSimpleName() + "." + component.getName() + " (nested, no @Valid)");
                            }
                        }
                    }
                }
            }
        }
        assertEquals(new TreeSet<String>(), offenders,
            "These bodies declare constraints that never run: add @Valid");
    }

    private static boolean reachesConstrainedRecord(AnnotatedType type) {
        if (type instanceof java.lang.reflect.AnnotatedParameterizedType parameterized) {
            for (AnnotatedType argument : parameterized.getAnnotatedActualTypeArguments()) {
                if (reachesConstrainedRecord(argument)) return true;
            }
            return false;
        }
        return type.getType() instanceof Class<?> cls && cls.isRecord() && declaresConstraints(cls);
    }

    /** @Valid somewhere on the path to the constrained record: the type itself or a type argument. */
    private static boolean cascades(AnnotatedType type) {
        if (type.isAnnotationPresent(jakarta.validation.Valid.class)) return true;
        if (type instanceof java.lang.reflect.AnnotatedParameterizedType parameterized) {
            for (AnnotatedType argument : parameterized.getAnnotatedActualTypeArguments()) {
                if (reachesConstrainedRecord(argument) && !cascades(argument)) return false;
            }
            return true;
        }
        return false;
    }

    private static boolean declaresConstraints(Class<?> record) {
        for (RecordComponent component : record.getRecordComponents()) {
            for (var annotation : component.getAnnotations()) {
                if (annotation.annotationType().isAnnotationPresent(jakarta.validation.Constraint.class)) return true;
            }
            for (var annotation : component.getAnnotatedType().getAnnotations()) {
                if (annotation.annotationType().isAnnotationPresent(jakarta.validation.Constraint.class)) return true;
            }
        }
        return false;
    }

    private static boolean hasHttpUrl(RecordComponent component) {
        AnnotatedType type = component.getAnnotatedType();
        return component.isAnnotationPresent(HttpUrl.class) || type.isAnnotationPresent(HttpUrl.class)
            || component.getAccessor().isAnnotationPresent(HttpUrl.class);
    }

    private static void collect(Type type, Set<Class<?>> into) {
        if (type instanceof ParameterizedType parameterized) {
            for (Type argument : parameterized.getActualTypeArguments()) collect(argument, into);
            return;
        }
        if (type instanceof Class<?> cls && cls.isRecord() && into.add(cls)) {
            for (RecordComponent component : cls.getRecordComponents()) {
                collect(component.getGenericType(), into);
            }
        }
    }
}
