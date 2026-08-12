package pl.nextsteppro.climbing.api.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.http.ResponseEntity;
import pl.nextsteppro.climbing.config.AppConfig;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The newsletter unsubscribe link is the one URL in this app that a stranger's software opens on
 * the recipient's behalf: corporate mail security (Defender Safe Links and the like) fetches every
 * link in an incoming message before the person reads it. While opening the link was the whole
 * action, that fetch unsubscribed them — silently, with no click and no way to tell afterwards
 * that it had happened. These tests hold the two halves apart.
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private static final String TOKEN = "unsubscribe-token-123";

    @Mock
    private UserService userService;
    @Mock
    private AppConfig appConfig;

    private MessageService msg;
    private UserController controller;

    @BeforeEach
    void setUp() {
        when(appConfig.getSiteUrl()).thenReturn("https://nextsteppro.pl");
        // The real bundles: these pages are the only HTML the backend hands a human, so a missing
        // key would surface as a 500 on a link that has to work.
        var source = new ReloadableResourceBundleMessageSource();
        source.setBasename("classpath:messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        msg = new MessageService(source);
        controller = new UserController(userService, msg, appConfig);
    }

    @Test
    void shouldNotUnsubscribeAnybodyWhenTheLinkIsMerelyOpened() {
        when(userService.resolveUnsubscribeLanguage(TOKEN)).thenReturn("pl");

        ResponseEntity<String> response = controller.unsubscribeConfirmation(TOKEN);

        assertEquals(200, response.getStatusCode().value());
        verify(userService).resolveUnsubscribeLanguage(TOKEN);
        verify(userService, never()).unsubscribeByToken(TOKEN);
    }

    /** Scanners follow links; they do not submit forms. That is the whole defence, so it must be a form. */
    @Test
    void shouldOfferAFormThatPostsBackRatherThanALinkThatActs() {
        when(userService.resolveUnsubscribeLanguage(TOKEN)).thenReturn("pl");

        String body = controller.unsubscribeConfirmation(TOKEN).getBody();

        assertNotNull(body);
        assertTrue(body.contains("<form method=\"post\" action=\"/api/user/unsubscribe\">"),
            "the confirmation page must submit a POST, not act on being rendered");
        assertTrue(body.contains(TOKEN), "the form has to carry the token back");
    }

    @Test
    void shouldUnsubscribeOnlyWhenTheFormIsSubmitted() {
        when(userService.unsubscribeByToken(TOKEN)).thenReturn("pl");

        ResponseEntity<String> response = controller.unsubscribe(TOKEN);

        assertEquals(200, response.getStatusCode().value());
        verify(userService).unsubscribeByToken(TOKEN);
    }

    @Test
    void shouldShowTheInvalidLinkPageWhenTheTokenIsStale() {
        when(userService.resolveUnsubscribeLanguage(TOKEN))
            .thenThrow(new IllegalArgumentException("Invalid unsubscribe token"));
        // Set the locale rather than inherit the JVM default: otherwise this test asserts on
        // whatever language the machine running it happens to be in.
        LocaleContextHolder.setLocale(Locale.of("pl"));
        try {
            ResponseEntity<String> response = controller.unsubscribeConfirmation(TOKEN);

            assertEquals(400, response.getStatusCode().value());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().contains("Nieprawidłowy link"));
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    void shouldShowTheInvalidLinkPageWhenTheSubmittedTokenIsStale() {
        when(userService.unsubscribeByToken(TOKEN))
            .thenThrow(new IllegalArgumentException("Invalid unsubscribe token"));

        ResponseEntity<String> response = controller.unsubscribe(TOKEN);

        assertEquals(400, response.getStatusCode().value());
    }

    /** Accented text on a hand-built page: without an explicit charset the browser is guessing. */
    @Test
    void shouldDeclareUtf8OnTheHtmlPages() {
        when(userService.unsubscribeByToken(TOKEN)).thenReturn("pl");

        ResponseEntity<String> response = controller.unsubscribe(TOKEN);

        assertEquals("text/html;charset=UTF-8", String.valueOf(response.getHeaders().getContentType()));
    }

    // ========== LANGUAGE ==========

    /**
     * The page speaks the language the email did. The recipient's profile is the source, not the
     * browser: someone reading a Spanish newsletter on a borrowed laptop still gets Spanish.
     */
    @Test
    void shouldRenderTheConfirmationPageInTheRecipientsLanguage() {
        when(userService.resolveUnsubscribeLanguage(TOKEN)).thenReturn("es");

        String body = controller.unsubscribeConfirmation(TOKEN).getBody();

        assertNotNull(body);
        assertTrue(body.contains("Darme de baja"), "expected the Spanish button, got: " + body);
        assertTrue(body.contains("<html lang=\"es\">"), "the page must declare its language");
    }

    @Test
    void shouldRenderTheDonePageInTheRecipientsLanguage() {
        when(userService.unsubscribeByToken(TOKEN)).thenReturn("en");

        String body = controller.unsubscribe(TOKEN).getBody();

        assertNotNull(body);
        assertTrue(body.contains("You have been unsubscribed"), "expected the English page, got: " + body);
        assertTrue(body.contains("Back to the homepage"));
    }

    /** A dead token has no user, so there is nobody whose language to speak — ask the browser. */
    @Test
    void shouldFallBackToTheBrowserLanguageOnTheInvalidLinkPage() {
        when(userService.resolveUnsubscribeLanguage(TOKEN))
            .thenThrow(new IllegalArgumentException("Invalid unsubscribe token"));
        LocaleContextHolder.setLocale(Locale.of("en"));
        try {
            String body = controller.unsubscribeConfirmation(TOKEN).getBody();

            assertNotNull(body);
            assertTrue(body.contains("Invalid link"), "expected the English page, got: " + body);
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }
}
