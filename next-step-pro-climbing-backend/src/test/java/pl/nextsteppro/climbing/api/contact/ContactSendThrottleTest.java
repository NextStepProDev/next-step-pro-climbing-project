package pl.nextsteppro.climbing.api.contact;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactSendThrottleTest {

    @Test
    void shouldCapAGuestAddressPerHour() {
        ContactSendThrottle throttle = new ContactSendThrottle(100);
        for (int i = 0; i < ContactSendThrottle.GUEST_PER_HOUR; i++) {
            assertTrue(throttle.tryGuest("203.0.113.7"));
        }
        assertFalse(throttle.tryGuest("203.0.113.7"));
        assertTrue(throttle.tryGuest("203.0.113.8"), "a neighbour on another address is not throttled");
    }

    /**
     * An IPv6 subscriber is handed a whole /64 — more addresses than the cap could ever count — so
     * keying on the full address lets one machine rotate past the cap with every request.
     */
    @Test
    void shouldCountAWholeIpv6Slash64AsOneSender() {
        ContactSendThrottle throttle = new ContactSendThrottle(100);
        for (int i = 0; i < ContactSendThrottle.GUEST_PER_HOUR; i++) {
            assertTrue(throttle.tryGuest("2001:db8:abcd:12::" + Integer.toHexString(i + 1)));
        }
        assertFalse(throttle.tryGuest("2001:db8:abcd:0012:ffff:ffff:ffff:ffff"),
            "another address in the same /64, written out in full, is the same sender");
        assertTrue(throttle.tryGuest("2001:db8:abcd:13::1"), "the next /64 is somebody else");
    }

    @Test
    void shouldKeepAnUnparseableAddressAsItIs() {
        ContactSendThrottle throttle = new ContactSendThrottle(100);
        // Never resolved as a host name: a header value must not trigger a DNS lookup
        for (int i = 0; i < ContactSendThrottle.GUEST_PER_HOUR; i++) {
            assertTrue(throttle.tryGuest("not-an-ip.example.com"));
        }
        assertFalse(throttle.tryGuest("not-an-ip.example.com"));
    }

    /**
     * Rotating addresses defeats any per-address cap, and every guest message is a mail in the
     * team's inbox. Past the hourly ceiling the message is still kept (the panel shows it); only
     * the mail stops.
     */
    @Test
    void shouldStopForwardingGuestMailPastTheHourlyCeiling() {
        ContactSendThrottle throttle = new ContactSendThrottle(3);
        assertTrue(throttle.tryForwardGuestMail());
        assertTrue(throttle.tryForwardGuestMail());
        assertTrue(throttle.tryForwardGuestMail());
        assertFalse(throttle.tryForwardGuestMail());
    }
}
