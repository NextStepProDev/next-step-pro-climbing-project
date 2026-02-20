package pl.nextsteppro.climbing.infrastructure.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class MessageService {

    private final MessageSource messageSource;

    public MessageService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * Get message for current request locale (from Accept-Language header).
     */
    public String get(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    /**
     * Get message for a specific locale (e.g. user's preferred language for emails).
     */
    public String get(String key, Locale locale, Object... args) {
        return messageSource.getMessage(key, args, locale);
    }

    /**
     * Get message for a language code string (convenience for email sending).
     */
    public String get(String key, String language, Object... args) {
        Locale locale = language != null ? Locale.of(language) : Locale.of("pl");
        return messageSource.getMessage(key, args, locale);
    }
}
