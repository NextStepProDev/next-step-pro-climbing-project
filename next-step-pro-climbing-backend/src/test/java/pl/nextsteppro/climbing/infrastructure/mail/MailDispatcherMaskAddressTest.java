package pl.nextsteppro.climbing.infrastructure.mail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The server log rotates by size, not by date, so it outlives account deletion — a full address
 * there is personal data the erasure cannot reach.
 */
class MailDispatcherMaskAddressTest {

    @Test
    void shouldKeepOnlyTheFirstLetterAndTheDomain() {
        assertEquals("j***@gmail.com", MailDispatcher.maskAddress("jan.kowalski@gmail.com"));
    }

    @Test
    void shouldHideEverythingWhenThereIsNoLocalPart() {
        assertEquals("***", MailDispatcher.maskAddress("not-an-address"));
        assertEquals("***", MailDispatcher.maskAddress("@nobody.pl"));
    }
}
