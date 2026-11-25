package pl.nextsteppro.climbing.infrastructure.mail;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.nextsteppro.climbing.config.AdminEmailConfig;
import pl.nextsteppro.climbing.config.AppConfig;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContactMailServiceTest {

    private final MailDispatcher dispatcher = mock(MailDispatcher.class);
    private final MessageService msg = mock(MessageService.class);
    private final AppConfig appConfig = new AppConfig();
    private final ContactMailService service;

    ContactMailServiceTest() {
        // The text itself is the message bundle's business; here a key stands for its text, with
        // the arguments appended so that what reaches the template can be checked.
        when(msg.getForLang(anyString(), anyString(), any(Object[].class))).thenAnswer(invocation -> {
            Object[] all = invocation.getArguments();
            StringBuilder text = new StringBuilder((String) all[0]);
            for (int i = 2; i < all.length; i++) text.append(' ').append(all[i]);
            return text.toString();
        });
        when(msg.getForLang(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new ContactMailService(dispatcher, appConfig, mock(AdminEmailConfig.class), msg);
    }

    @Test
    void shouldRemindWithALinkToTheChatAndNoContent() {
        service.sendUnseenReminder("ala@example.com", "Ala", "pl");

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(dispatcher).sendHtml(eq("ala@example.com"), eq("email.contact.unseen.subject"), html.capture());
        String body = html.getValue();
        assertTrue(body.contains("href=\"" + appConfig.getSiteUrl() + "/my-reservations?tab=messages\""), "the way into the chat");
        assertTrue(body.contains("email.contact.unseen.hint"), "where to turn these mails off");
        assertEquals(-1, body.indexOf("email.contact.reply"), "no part of the reply mail, which carries the message");
    }

    // A name from Google arrives unvalidated; in a mail it is data, never markup.
    @Test
    void shouldEscapeTheNameInTheGreeting() {
        service.sendUnseenReminder("x@example.com", "<b>Ala</b>", "pl");

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(dispatcher).sendHtml(anyString(), anyString(), html.capture());
        assertFalse(html.getValue().contains("<b>Ala</b>"));
        assertTrue(html.getValue().contains("&lt;b&gt;Ala&lt;/b&gt;"));
    }
}
