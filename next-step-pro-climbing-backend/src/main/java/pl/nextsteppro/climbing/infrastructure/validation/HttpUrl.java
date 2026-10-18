package pl.nextsteppro.climbing.infrastructure.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.util.Locale;

/**
 * An absolute http(s) URL, or nothing (null and blank pass — the field may be cleared).
 *
 * <p>Every URL a person types into this application ends up as an {@code href}, an {@code src} or an
 * attribute in a public OG stub. {@code javascript:} in the first, or a quote in the last, is enough;
 * a typo is the usual way in, malice the rarer one. Rejected at the door instead of escaped at each
 * of the places it is later printed. Guarded by {@code UrlFieldsValidatedTest}.
 */
@Documented
@Constraint(validatedBy = HttpUrl.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpUrl {

    String message() default "{validation.url.http}";

    /**
     * Also accept a path on this site ({@code /kursy}) — for links the SPA routes itself. Never a
     * protocol-relative {@code //host}, which is another site wearing a path's clothes.
     */
    boolean allowSitePath() default false;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<HttpUrl, String> {
        private boolean allowSitePath;

        @Override
        public void initialize(HttpUrl annotation) {
            this.allowSitePath = annotation.allowSitePath();
        }

        @Override
        public boolean isValid(@Nullable String value, ConstraintValidatorContext context) {
            return value == null || value.isBlank() || isHttpUrl(value)
                || (allowSitePath && isSitePath(value));
        }

        public static boolean isSitePath(String value) {
            String trimmed = value.trim();
            return trimmed.startsWith("/") && !trimmed.startsWith("//") && !trimmed.startsWith("/\\")
                && trimmed.chars().noneMatch(c -> c == '"' || c == '\'' || c == '<' || c == '>'
                    || c == '\\' || Character.isWhitespace(c));
        }

        public static boolean isHttpUrl(String value) {
            String trimmed = value.trim();
            // Quotes, angle brackets and whitespace have no business in a URL a person pastes, and they
            // are exactly what breaks out of an attribute.
            if (trimmed.chars().anyMatch(c -> c == '"' || c == '\'' || c == '<' || c == '>' || Character.isWhitespace(c))) {
                return false;
            }
            try {
                URI uri = new URI(trimmed);
                String scheme = uri.getScheme();
                return scheme != null
                    && (scheme.toLowerCase(Locale.ROOT).equals("http") || scheme.toLowerCase(Locale.ROOT).equals("https"))
                    && uri.getHost() != null;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
