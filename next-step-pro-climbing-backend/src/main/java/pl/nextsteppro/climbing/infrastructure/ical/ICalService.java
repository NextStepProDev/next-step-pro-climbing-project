package pl.nextsteppro.climbing.infrastructure.ical;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.*;
import net.fortuna.ical4j.model.property.immutable.ImmutableCalScale;
import net.fortuna.ical4j.model.property.immutable.ImmutableMethod;
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class ICalService {

    private static final Logger log = LoggerFactory.getLogger(ICalService.class);
    private static final ZoneId ZONE_ID = ZoneId.of("Europe/Warsaw");

    public byte[] generateReservationIcs(Reservation reservation) {
        User user = reservation.getUser();
        TimeSlot slot = reservation.getTimeSlot();

        LocalDateTime startDateTime = LocalDateTime.of(slot.getDate(), slot.getStartTime());
        LocalDateTime endDateTime = LocalDateTime.of(slot.getDate(), slot.getEndTime());

        String eventTitle = "Wspinaczka: " + user.getFullName();
        String description = buildDescription(user, slot);

        return generateIcs(eventTitle, description, startDateTime, endDateTime, reservation.getId().toString());
    }

    public byte[] generateEventIcs(pl.nextsteppro.climbing.domain.event.Event event) {
        LocalDateTime startDateTime = event.getStartDate().atTime(9, 0);
        LocalDateTime endDateTime = event.getEndDate().atTime(17, 0);

        String description = event.getDescription() != null ? event.getDescription() : "";

        return generateIcs(event.getTitle(), description, startDateTime, endDateTime, event.getId().toString());
    }

    private byte[] generateIcs(String title, String description, LocalDateTime start, LocalDateTime end, String uid) {
        try {
            var startInstant = start.atZone(ZONE_ID).toInstant();
            var endInstant = end.atZone(ZONE_ID).toInstant();

            VEvent event = new VEvent(startInstant, endInstant, title);
            event.add(new Uid(uid + "@nextsteppro.pl"));
            event.add(new Description(description));
            event.add(new Location("Next Step Pro Climbing"));

            Calendar calendar = new Calendar();
            calendar.add(new ProdId("-//Next Step Pro Climbing//Reservation System//PL"));
            calendar.add(ImmutableVersion.VERSION_2_0);
            calendar.add(ImmutableCalScale.GREGORIAN);
            calendar.add(ImmutableMethod.PUBLISH);
            calendar.add(event);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            new CalendarOutputter().output(calendar, out);

            return out.toByteArray();
        } catch (IOException e) {
            log.error("Failed to generate ICS file", e);
            return new byte[0];
        }
    }

    private String buildDescription(User user, TimeSlot slot) {
        return """
            Klient: %s
            Email: %s
            Telefon: %s
            """.formatted(user.getFullName(), user.getEmail(), user.getPhone());
    }
}
