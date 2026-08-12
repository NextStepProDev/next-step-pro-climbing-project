package pl.nextsteppro.climbing.api.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import pl.nextsteppro.climbing.config.AppConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
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

    private UserController controller;

    @BeforeEach
    void setUp() {
        when(appConfig.getSiteUrl()).thenReturn("https://nextsteppro.pl");
        controller = new UserController(userService, appConfig);
    }

    @Test
    void shouldNotUnsubscribeAnybodyWhenTheLinkIsMerelyOpened() {
        ResponseEntity<String> response = controller.unsubscribeConfirmation(TOKEN);

        assertEquals(200, response.getStatusCode().value());
        verify(userService).requireUnsubscribeToken(TOKEN);
        verify(userService, never()).unsubscribeByToken(TOKEN);
    }

    /** Scanners follow links; they do not submit forms. That is the whole defence, so it must be a form. */
    @Test
    void shouldOfferAFormThatPostsBackRatherThanALinkThatActs() {
        String body = controller.unsubscribeConfirmation(TOKEN).getBody();

        assertNotNull(body);
        assertTrue(body.contains("<form method=\"post\" action=\"/api/user/unsubscribe\">"),
            "the confirmation page must submit a POST, not act on being rendered");
        assertTrue(body.contains(TOKEN), "the form has to carry the token back");
    }

    @Test
    void shouldUnsubscribeOnlyWhenTheFormIsSubmitted() {
        ResponseEntity<String> response = controller.unsubscribe(TOKEN);

        assertEquals(200, response.getStatusCode().value());
        verify(userService).unsubscribeByToken(TOKEN);
    }

    @Test
    void shouldShowTheInvalidLinkPageWhenTheTokenIsStale() {
        doThrow(new IllegalArgumentException("Invalid unsubscribe token"))
            .when(userService).requireUnsubscribeToken(TOKEN);

        ResponseEntity<String> response = controller.unsubscribeConfirmation(TOKEN);

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Nieprawidłowy link"));
    }

    @Test
    void shouldShowTheInvalidLinkPageWhenTheSubmittedTokenIsStale() {
        doThrow(new IllegalArgumentException("Invalid unsubscribe token"))
            .when(userService).unsubscribeByToken(TOKEN);

        ResponseEntity<String> response = controller.unsubscribe(TOKEN);

        assertEquals(400, response.getStatusCode().value());
    }

    /** Polish text on a hand-built page: without an explicit charset the browser is guessing. */
    @Test
    void shouldDeclareUtf8OnTheHtmlPages() {
        ResponseEntity<String> response = controller.unsubscribe(TOKEN);

        assertEquals("text/html;charset=UTF-8", String.valueOf(response.getHeaders().getContentType()));
    }
}
