package pl.nextsteppro.climbing.api.contact;

import org.junit.jupiter.api.Test;
import pl.nextsteppro.climbing.domain.contact.ContactConversation;
import pl.nextsteppro.climbing.domain.contact.ContactConversationRepository;
import pl.nextsteppro.climbing.domain.contact.ContactMessage;
import pl.nextsteppro.climbing.domain.contact.ContactMessageRepository;
import pl.nextsteppro.climbing.domain.course.CourseRepository;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.infrastructure.mail.ContactMailService;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Past the hourly guest-mail ceiling the message is kept and only the mail is skipped. */
class ContactServiceMailCeilingTest {

    private final ContactConversationRepository conversations = mock(ContactConversationRepository.class);
    private final ContactMessageRepository messages = mock(ContactMessageRepository.class);
    private final ContactMailService mail = mock(ContactMailService.class);

    private ContactService service(int guestMailPerHour) {
        when(conversations.findByGuestEmail(anyString())).thenReturn(Optional.empty());
        when(conversations.save(any(ContactConversation.class))).thenAnswer(inv -> inv.getArgument(0));
        return new ContactService(conversations, messages, mock(UserRepository.class), mock(CourseRepository.class),
            new ContactSendThrottle(guestMailPerHour), mail);
    }

    private static SendContactMessageRequest guest(int n) {
        return new SendContactMessageRequest("Bot " + n, "bot" + n + "@example.com", "spam " + n, true,
            null, null, null, null, "pl");
    }

    @Test
    void shouldKeepEveryGuestMessageButStopMailingPastTheCeiling() {
        ContactService service = service(2);

        for (int i = 0; i < 4; i++) {
            service.send(null, "198.51.100." + i, guest(i)); // a different address every time
        }

        verify(messages, times(4)).save(any(ContactMessage.class));
        verify(mail, times(2)).sendInboundToStaff(any(), anyString(), anyString(), anyBoolean(), anyString(), any(), anyString());
    }
}
