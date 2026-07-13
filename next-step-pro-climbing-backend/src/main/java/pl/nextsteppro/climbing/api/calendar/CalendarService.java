package pl.nextsteppro.climbing.api.calendar;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.course.Course;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventRepository;
import pl.nextsteppro.climbing.domain.event.EventType;
import pl.nextsteppro.climbing.domain.reservation.GuestReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.ReservationStatus;
import pl.nextsteppro.climbing.domain.reservation.SlotParticipantCount;
import pl.nextsteppro.climbing.domain.reservedseat.ReservedSeatCount;
import pl.nextsteppro.climbing.domain.reservedseat.ReservedSeatRepository;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlotRepository;
import pl.nextsteppro.climbing.domain.waitlist.EventWaitlist;
import pl.nextsteppro.climbing.domain.waitlist.EventWaitlistRepository;
import pl.nextsteppro.climbing.domain.waitlist.Waitlist;
import pl.nextsteppro.climbing.domain.waitlist.WaitlistRepository;
import pl.nextsteppro.climbing.domain.waitlist.WaitlistStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CalendarService {

    private final TimeSlotRepository timeSlotRepository;
    private final ReservationRepository reservationRepository;
    private final GuestReservationRepository guestReservationRepository;
    private final EventRepository eventRepository;
    private final WaitlistRepository waitlistRepository;
    private final EventWaitlistRepository eventWaitlistRepository;
    private final ReservedSeatRepository reservedSeatRepository;

    public CalendarService(TimeSlotRepository timeSlotRepository,
                          ReservationRepository reservationRepository,
                          GuestReservationRepository guestReservationRepository,
                          EventRepository eventRepository,
                          WaitlistRepository waitlistRepository,
                          EventWaitlistRepository eventWaitlistRepository,
                          ReservedSeatRepository reservedSeatRepository) {
        this.timeSlotRepository = timeSlotRepository;
        this.reservationRepository = reservationRepository;
        this.guestReservationRepository = guestReservationRepository;
        this.eventRepository = eventRepository;
        this.waitlistRepository = waitlistRepository;
        this.eventWaitlistRepository = eventWaitlistRepository;
        this.reservedSeatRepository = reservedSeatRepository;
    }

    @Cacheable(value = "calendarMonth", key = "#yearMonth", condition = "#userId == null")
    public MonthViewDto getMonthView(YearMonth yearMonth, @Nullable UUID userId) {
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        List<TimeSlot> slots = timeSlotRepository.findByDateRangeOrdered(startDate, endDate);
        List<Event> events = eventRepository.findActiveEventsBetween(startDate, endDate);

        // Batch: load all confirmed counts at once
        List<UUID> allSlotIds = slots.stream().map(TimeSlot::getId).toList();
        Map<UUID, Integer> countMap = buildCountMap(allSlotIds);
        Map<UUID, Integer> inviteMap = buildSlotInviteMap(allSlotIds);

        // Batch: load user's confirmed slot IDs at once
        Set<UUID> userConfirmedSlotIds = userId != null && !allSlotIds.isEmpty()
            ? new HashSet<>(reservationRepository.findUserConfirmedSlotIds(userId, allSlotIds))
            : Set.of();
        Set<UUID> userInvitedSlotIds = userId != null && !allSlotIds.isEmpty()
            ? new HashSet<>(reservedSeatRepository.findUserPendingSlotInviteIds(userId, allSlotIds))
            : Set.of();

        Map<LocalDate, List<TimeSlot>> slotsByDate = slots.stream()
            .collect(Collectors.groupingBy(TimeSlot::getDate));

        List<DaySummaryDto> days = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            List<TimeSlot> daySlots = slotsByDate.getOrDefault(date, List.of());
            days.add(createDaySummary(date, daySlots, countMap, userConfirmedSlotIds, inviteMap, userInvitedSlotIds));
        }

        EventData eventData = computeEventData(events, userId);

        List<EventSummaryDto> eventSummaries = events.stream()
            .map(event -> toEventSummary(event, eventData.participantsMap().getOrDefault(event.getId(), 0),
                                         eventData.userRegisteredEventIds().contains(event.getId()),
                                         eventData.inviteMap().getOrDefault(event.getId(), 0),
                                         eventData.userInvitedEventIds().contains(event.getId())))
            .toList();

        return new MonthViewDto(yearMonth.toString(), days, eventSummaries);
    }

    @Cacheable(value = "calendarWeek", key = "#weekStart", condition = "#userId == null")
    public WeekViewDto getWeekView(LocalDate weekStart, @Nullable UUID userId) {
        LocalDate startDate = weekStart;
        LocalDate endDate = weekStart.plusDays(6);

        List<TimeSlot> slots = timeSlotRepository.findByDateRangeOrdered(startDate, endDate);
        List<Event> events = eventRepository.findActiveEventsBetween(startDate, endDate);

        List<UUID> allSlotIds = slots.stream().map(TimeSlot::getId).toList();
        Map<UUID, Integer> countMap = buildCountMap(allSlotIds);
        Map<UUID, Integer> inviteMap = buildSlotInviteMap(allSlotIds);
        Set<UUID> userConfirmedSlotIds = userId != null && !allSlotIds.isEmpty()
            ? new HashSet<>(reservationRepository.findUserConfirmedSlotIds(userId, allSlotIds))
            : Set.of();
        Set<UUID> userInvitedSlotIds = userId != null && !allSlotIds.isEmpty()
            ? new HashSet<>(reservedSeatRepository.findUserPendingSlotInviteIds(userId, allSlotIds))
            : Set.of();

        Map<LocalDate, List<TimeSlot>> slotsByDate = slots.stream()
            .collect(Collectors.groupingBy(TimeSlot::getDate));

        List<WeekDayDto> days = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            List<TimeSlot> daySlots = slotsByDate.getOrDefault(date, List.of());
            List<TimeSlotDto> slotDtos = daySlots.stream()
                .filter(slot -> !slot.belongsToEvent())
                .map(slot -> toTimeSlotDto(slot, countMap.getOrDefault(slot.getId(), 0),
                                           userConfirmedSlotIds.contains(slot.getId()),
                                           inviteMap.getOrDefault(slot.getId(), 0),
                                           userInvitedSlotIds.contains(slot.getId())))
                .toList();
            days.add(new WeekDayDto(date, slotDtos));
        }

        EventData eventData = computeEventData(events, userId);
        List<EventSummaryDto> eventSummaries = events.stream()
            .map(event -> toEventSummary(event, eventData.participantsMap().getOrDefault(event.getId(), 0),
                                         eventData.userRegisteredEventIds().contains(event.getId()),
                                         eventData.inviteMap().getOrDefault(event.getId(), 0),
                                         eventData.userInvitedEventIds().contains(event.getId())))
            .toList();

        return new WeekViewDto(startDate, endDate, days, eventSummaries);
    }

    @Cacheable(value = "calendarDay", key = "#date", condition = "#userId == null")
    public DayViewDto getDayView(LocalDate date, @Nullable UUID userId) {
        List<TimeSlot> slots = timeSlotRepository.findByDateSorted(date);
        List<Event> events = eventRepository.findActiveEventsOnDate(date);

        List<UUID> slotIds = slots.stream().map(TimeSlot::getId).toList();
        Map<UUID, Integer> countMap = buildCountMap(slotIds);
        Map<UUID, Integer> inviteMap = buildSlotInviteMap(slotIds);
        Set<UUID> userConfirmedSlotIds = userId != null && !slotIds.isEmpty()
            ? new HashSet<>(reservationRepository.findUserConfirmedSlotIds(userId, slotIds))
            : Set.of();
        Set<UUID> userInvitedSlotIds = userId != null && !slotIds.isEmpty()
            ? new HashSet<>(reservedSeatRepository.findUserPendingSlotInviteIds(userId, slotIds))
            : Set.of();

        List<TimeSlotDto> slotDtos = slots.stream()
            .filter(slot -> !slot.belongsToEvent())
            .map(slot -> toTimeSlotDto(slot, countMap.getOrDefault(slot.getId(), 0),
                                       userConfirmedSlotIds.contains(slot.getId()),
                                       inviteMap.getOrDefault(slot.getId(), 0),
                                       userInvitedSlotIds.contains(slot.getId())))
            .toList();

        EventData eventData = computeEventData(events, userId);

        List<EventSummaryDto> eventSummaries = events.stream()
            .map(event -> toEventSummary(event, eventData.participantsMap().getOrDefault(event.getId(), 0),
                                         eventData.userRegisteredEventIds().contains(event.getId()),
                                         eventData.inviteMap().getOrDefault(event.getId(), 0),
                                         eventData.userInvitedEventIds().contains(event.getId())))
            .toList();

        return new DayViewDto(date, slotDtos, eventSummaries);
    }

    public EventSummaryDto getEventSummary(UUID eventId, @Nullable UUID userId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));

        EventData eventData = computeEventData(List.of(event), userId);
        int currentParticipants = eventData.participantsMap().getOrDefault(event.getId(), 0);
        boolean isUserRegistered = eventData.userRegisteredEventIds().contains(event.getId());

        // Waitlist status for the logged-in user
        @Nullable WaitlistStatus userWaitlistStatus = null;
        @Nullable UUID waitlistEntryId = null;
        @Nullable Instant confirmationDeadline = null;
        int userWaitlistPosition = 0;

        if (userId != null) {
            EventWaitlist waitlistEntry = eventWaitlistRepository.findByUserIdAndEventId(userId, eventId).orElse(null);
            if (waitlistEntry != null && (waitlistEntry.isWaiting() || waitlistEntry.isPendingConfirmation())) {
                userWaitlistStatus = waitlistEntry.getStatus();
                waitlistEntryId = waitlistEntry.getId();
                confirmationDeadline = waitlistEntry.getConfirmationDeadline();
                userWaitlistPosition = eventWaitlistRepository.countWaitingAtOrBeforePosition(eventId, waitlistEntry.getPosition());
                userWaitlistPosition = Math.max(1, userWaitlistPosition);
            }
        }

        boolean isReservedForUser = eventData.userInvitedEventIds().contains(event.getId());

        LocalDateTime eventStart = LocalDateTime.of(
            event.getStartDate(),
            event.getStartTime() != null ? event.getStartTime() : LocalTime.of(0, 0)
        );
        // The 12 h window does NOT apply to invitees — they can confirm their seat until the last moment.
        boolean enrollmentOpen = !event.getEventType().blocksEnrollment()
            && (isReservedForUser || eventStart.isAfter(LocalDateTime.now(WARSAW).plusHours(BOOKING_CUTOFF_HOURS)));

        Course course = event.getCourse();
        String title = course != null ? course.getTitle() : event.getTitle();
        UUID courseId = course != null ? course.getId() : null;
        boolean coursePublished = course != null && course.isPublished();

        int userParticipants = 0;
        if (userId != null && isUserRegistered) {
            List<Integer> participants = reservationRepository.findUserParticipantsForEvent(userId, eventId);
            if (!participants.isEmpty()) {
                userParticipants = participants.getFirst();
            }
        }

        int reservedSeats = eventData.inviteMap().getOrDefault(event.getId(), 0);

        return new EventSummaryDto(
            event.getId(), title, event.getDescription(), event.getLocation(),
            event.getEventType().name(), event.getStartDate(), event.getEndDate(),
            event.getStartTime(), event.getEndTime(), event.isMultiDay(),
            event.getMaxParticipants(), currentParticipants, isUserRegistered, enrollmentOpen,
            courseId, coursePublished,
            userWaitlistStatus, waitlistEntryId, confirmationDeadline, userWaitlistPosition,
            userParticipants,
            reservedSeats, isReservedForUser
        );
    }

    /** Lightweight projection for OG link previews (no auth/waitlist context). */
    public EventOgView getEventOgView(UUID eventId) {
        EventSummaryDto summary = getEventSummary(eventId, null);
        return new EventOgView(
            summary.title(), summary.location(), summary.startDate(), summary.endDate(),
            summary.courseId(), summary.coursePublished()
        );
    }

    public TimeSlotDetailDto getSlotDetails(UUID slotId, @Nullable UUID userId) {
        TimeSlot slot = timeSlotRepository.findById(slotId)
            .orElseThrow(() -> new IllegalArgumentException("Time slot not found: " + slotId));

        int confirmedCount = reservationRepository.countConfirmedByTimeSlotId(slotId)
            + guestReservationRepository.sumParticipantsByTimeSlotId(slotId);
        int pendingWaitlistCount = waitlistRepository.countPendingConfirmationBySlotId(slotId);
        // The effective taken-seat count includes PENDING_CONFIRMATION from the waitlist
        int effectiveCount = confirmedCount + pendingWaitlistCount;

        int reservedSeats = reservedSeatRepository.countPendingBySlotId(slotId);

        boolean isUserRegistered = false;
        boolean isReservedForUser = false;
        @Nullable UUID reservationId = null;
        @Nullable WaitlistStatus userWaitlistStatus = null;
        @Nullable UUID waitlistEntryId = null;
        @Nullable Instant confirmationDeadline = null;
        int userWaitlistPosition = 0;

        if (userId != null) {
            isReservedForUser = reservedSeatRepository.existsPendingBySlotIdAndUserId(slotId, userId);
            Reservation reservation = reservationRepository.findByUserIdAndTimeSlotId(userId, slotId);
            if (reservation != null && reservation.getStatus() == ReservationStatus.CONFIRMED) {
                isUserRegistered = true;
                reservationId = reservation.getId();
            }

            Waitlist waitlistEntry = waitlistRepository.findByUserIdAndSlotId(userId, slotId).orElse(null);
            if (waitlistEntry != null && (waitlistEntry.isWaiting() || waitlistEntry.isPendingConfirmation())) {
                userWaitlistStatus = waitlistEntry.getStatus();
                waitlistEntryId = waitlistEntry.getId();
                confirmationDeadline = waitlistEntry.getConfirmationDeadline();
                userWaitlistPosition = waitlistRepository.countWaitingAtOrBeforePosition(slotId, waitlistEntry.getPosition());
                userWaitlistPosition = Math.max(1, userWaitlistPosition);
            }
        }

        SlotStatus status = determineSlotStatus(slot, effectiveCount, reservedSeats, isReservedForUser);

        return new TimeSlotDetailDto(
            slot.getId(),
            slot.getDate(),
            slot.getStartTime(),
            slot.getEndTime(),
            slot.getMaxParticipants(),
            confirmedCount,
            status,
            isUserRegistered,
            slot.belongsToEvent() ? slot.getEvent().getId() : null,
            slot.getDisplayTitle(),
            slot.belongsToEvent() ? slot.getEvent().getDescription() : null,
            reservationId,
            userWaitlistStatus,
            waitlistEntryId,
            confirmationDeadline,
            userWaitlistPosition,
            slot.isAvailabilityWindow(),
            slot.getTitle(),
            reservedSeats,
            isReservedForUser
        );
    }

    public List<CourseEventDto> getCourseEvents(UUID courseId) {
        List<Event> events = eventRepository.findUpcomingByCourseId(courseId, LocalDate.now(WARSAW));
        if (events.isEmpty()) return List.of();

        EventData eventData = computeEventData(events, null);

        return events.stream()
            .map(event -> {
                int participants = eventData.participantsMap().getOrDefault(event.getId(), 0);
                int availableSpots = Math.max(0, event.getMaxParticipants() - participants);

                LocalDateTime eventStart = LocalDateTime.of(
                    event.getStartDate(),
                    event.getStartTime() != null ? event.getStartTime() : LocalTime.of(0, 0)
                );
                LocalDateTime now = LocalDateTime.now(WARSAW);
                SlotStatus status;
                if (eventStart.isBefore(now)) {
                    status = SlotStatus.PAST;
                } else if (participants >= event.getMaxParticipants()) {
                    status = SlotStatus.FULL;
                } else if (eventStart.isBefore(now.plusHours(BOOKING_CUTOFF_HOURS))) {
                    status = SlotStatus.BOOKING_CLOSED;
                } else {
                    status = SlotStatus.AVAILABLE;
                }

                return new CourseEventDto(
                    event.getId(),
                    event.getStartDate(),
                    event.getEndDate(),
                    event.getStartTime(),
                    event.getEndTime(),
                    status,
                    availableSpots
                );
            })
            .toList();
    }

    public List<CourseEventDto> getCourseEventsByTranslationGroup(UUID translationGroupId) {
        List<Event> events = eventRepository.findUpcomingByTranslationGroupId(translationGroupId, LocalDate.now(WARSAW));
        if (events.isEmpty()) return List.of();

        EventData eventData = computeEventData(events, null);

        return events.stream()
            .map(event -> {
                int participants = eventData.participantsMap().getOrDefault(event.getId(), 0);
                int availableSpots = Math.max(0, event.getMaxParticipants() - participants);

                LocalDateTime eventStart = LocalDateTime.of(
                    event.getStartDate(),
                    event.getStartTime() != null ? event.getStartTime() : LocalTime.of(0, 0)
                );
                LocalDateTime now = LocalDateTime.now(WARSAW);
                SlotStatus status;
                if (eventStart.isBefore(now)) {
                    status = SlotStatus.PAST;
                } else if (participants >= event.getMaxParticipants()) {
                    status = SlotStatus.FULL;
                } else if (eventStart.isBefore(now.plusHours(BOOKING_CUTOFF_HOURS))) {
                    status = SlotStatus.BOOKING_CLOSED;
                } else {
                    status = SlotStatus.AVAILABLE;
                }

                return new CourseEventDto(
                    event.getId(),
                    event.getStartDate(),
                    event.getEndDate(),
                    event.getStartTime(),
                    event.getEndTime(),
                    status,
                    availableSpots
                );
            })
            .toList();
    }

    private DaySummaryDto createDaySummary(LocalDate date, List<TimeSlot> slots,
                                          Map<UUID, Integer> countMap, Set<UUID> userConfirmedSlotIds,
                                          Map<UUID, Integer> inviteMap, Set<UUID> userInvitedSlotIds) {
        List<TimeSlot> standaloneSlots = slots.stream()
            .filter(slot -> !slot.belongsToEvent())
            .toList();

        boolean hasAvailabilityWindow = standaloneSlots.stream()
            .anyMatch(TimeSlot::isAvailabilityWindow);

        List<TimeSlot> bookableSlots = standaloneSlots.stream()
            .filter(slot -> !slot.isAvailabilityWindow())
            .toList();

        int totalSlots = bookableSlots.size();
        int availableSlots = 0;
        boolean hasUserReservation = false;
        boolean hasReservedSeats = false;

        LocalDateTime cutoff = LocalDateTime.now(WARSAW).plusHours(BOOKING_CUTOFF_HOURS);
        for (TimeSlot slot : bookableSlots) {
            LocalDateTime slotDateTime = LocalDateTime.of(slot.getDate(), slot.getStartTime());
            if (!slot.isBlocked() && slotDateTime.isAfter(cutoff)) {
                int confirmed = countMap.getOrDefault(slot.getId(), 0);
                // Seats held for invitees count as taken; the viewer's own invitation does not.
                int reservedForOthers = inviteMap.getOrDefault(slot.getId(), 0)
                    - (userInvitedSlotIds.contains(slot.getId()) ? 1 : 0);
                if (confirmed + reservedForOthers < slot.getMaxParticipants()) {
                    availableSlots++;
                } else if (reservedForOthers > 0) {
                    // Full for this viewer, but only due to invitation-held seats —
                    // a signal for the frontend to nudge invitees to log in instead of showing "no seats".
                    hasReservedSeats = true;
                }
            }
            if (userConfirmedSlotIds.contains(slot.getId())) {
                hasUserReservation = true;
            }
        }

        return new DaySummaryDto(date, totalSlots, availableSlots, hasUserReservation, hasAvailabilityWindow, hasReservedSeats);
    }

    private TimeSlotDto toTimeSlotDto(TimeSlot slot, int confirmedCount, boolean isUserRegistered,
                                      int reservedSeats, boolean isReservedForUser) {
        SlotStatus status = determineSlotStatus(slot, confirmedCount, reservedSeats, isReservedForUser);

        return new TimeSlotDto(
            slot.getId(),
            slot.getStartTime(),
            slot.getEndTime(),
            slot.getMaxParticipants(),
            confirmedCount,
            status,
            isUserRegistered,
            slot.getDisplayTitle(),
            slot.isAvailabilityWindow(),
            reservedSeats,
            isReservedForUser
        );
    }

    private static final int BOOKING_CUTOFF_HOURS = 12;
    // Polish local time (prod container = UTC). See BookingTimeValidator — same correction for calendar views.
    private static final java.time.ZoneId WARSAW = java.time.ZoneId.of("Europe/Warsaw");

    private SlotStatus determineSlotStatus(TimeSlot slot, int confirmedCount, int reservedSeats, boolean isReservedForUser) {
        LocalDateTime slotDateTime = LocalDateTime.of(slot.getDate(), slot.getStartTime());
        LocalDateTime now = LocalDateTime.now(WARSAW);
        if (slot.isAvailabilityWindow()) {
            // The window lives until its END time (not start): an ongoing 9-15 window at 10:00 still
            // accepts training requests for the remaining hours — a PAST status would hide the request button.
            LocalDateTime windowEnd = LocalDateTime.of(slot.getDate(), slot.getEndTime());
            return windowEnd.isBefore(now) ? SlotStatus.PAST : SlotStatus.AVAILABILITY_WINDOW;
        }
        if (slotDateTime.isBefore(now)) {
            return SlotStatus.PAST;
        }
        if (slot.isBlocked()) {
            return SlotStatus.BLOCKED;
        }
        // Seats held for other invitees are unavailable to this viewer (FULL with reservedSeats>0 →
        // the frontend shows "reserved for invitees"). The viewer's own invitation does not block.
        int reservedForOthers = reservedSeats - (isReservedForUser ? 1 : 0);
        if (confirmedCount + reservedForOthers >= slot.getMaxParticipants()) {
            return SlotStatus.FULL;
        }
        // The 12 h window does NOT apply to invitees — their held seat can be taken until the last moment.
        if (!isReservedForUser && slotDateTime.isBefore(now.plusHours(BOOKING_CUTOFF_HOURS))) {
            return SlotStatus.BOOKING_CLOSED;
        }
        return SlotStatus.AVAILABLE;
    }

    private EventSummaryDto toEventSummary(Event event, int currentParticipants, boolean isUserRegistered,
                                           int reservedSeats, boolean isReservedForUser) {
        LocalDateTime eventStart = LocalDateTime.of(
            event.getStartDate(),
            event.getStartTime() != null ? event.getStartTime() : LocalTime.of(0, 0)
        );
        // The 12 h window does NOT apply to invitees — they can confirm their seat until the last moment.
        boolean enrollmentOpen = !event.getEventType().blocksEnrollment()
            && (isReservedForUser || eventStart.isAfter(LocalDateTime.now(WARSAW).plusHours(BOOKING_CUTOFF_HOURS)));
        Course course = event.getCourse();
        String title = course != null ? course.getTitle() : event.getTitle();
        UUID courseId = course != null ? course.getId() : null;
        boolean coursePublished = course != null && course.isPublished();
        return new EventSummaryDto(
            event.getId(), title, event.getDescription(), event.getLocation(),
            event.getEventType().name(), event.getStartDate(), event.getEndDate(),
            event.getStartTime(), event.getEndTime(), event.isMultiDay(),
            event.getMaxParticipants(), currentParticipants, isUserRegistered, enrollmentOpen,
            courseId, coursePublished,
            null, null, null, 0, 0,
            reservedSeats, isReservedForUser
        );
    }

    private record EventData(Map<UUID, Integer> participantsMap, Set<UUID> userRegisteredEventIds,
                             Map<UUID, Integer> inviteMap, Set<UUID> userInvitedEventIds) {}

    private EventData computeEventData(List<Event> events, @Nullable UUID userId) {
        if (events.isEmpty()) {
            return new EventData(Map.of(), Set.of(), Map.of(), Set.of());
        }

        List<UUID> eventIds = events.stream().map(Event::getId).toList();
        List<TimeSlot> allEventSlots = timeSlotRepository.findByEventIdIn(eventIds);
        List<UUID> allEventSlotIds = allEventSlots.stream().map(TimeSlot::getId).toList();
        Map<UUID, Integer> countMap = buildCountMap(allEventSlotIds);

        Map<UUID, Integer> participantsMap = new HashMap<>();
        for (TimeSlot slot : allEventSlots) {
            if (slot.belongsToEvent()) {
                UUID eventId = slot.getEvent().getId();
                int confirmed = countMap.getOrDefault(slot.getId(), 0);
                participantsMap.merge(eventId, confirmed, Math::max);
            }
        }
        // Add guests registered directly on the event (not on a slot)
        guestReservationRepository.sumParticipantsByEventIds(eventIds)
            .forEach(g -> participantsMap.merge(g.slotId(), g.countAsInt(), Integer::sum));

        Set<UUID> userRegisteredEventIds = new HashSet<>();
        if (userId != null && !allEventSlotIds.isEmpty()) {
            Set<UUID> userConfirmedSlotIds = new HashSet<>(
                reservationRepository.findUserConfirmedSlotIds(userId, allEventSlotIds));
            for (TimeSlot slot : allEventSlots) {
                if (slot.belongsToEvent() && userConfirmedSlotIds.contains(slot.getId())) {
                    userRegisteredEventIds.add(slot.getEvent().getId());
                }
            }
        }

        Map<UUID, Integer> inviteMap = reservedSeatRepository.countPendingByEventIds(eventIds).stream()
            .collect(Collectors.toMap(ReservedSeatCount::targetId, ReservedSeatCount::countAsInt));
        Set<UUID> userInvitedEventIds = userId != null
            ? new HashSet<>(reservedSeatRepository.findUserPendingEventInviteIds(userId, eventIds))
            : Set.of();

        return new EventData(participantsMap, userRegisteredEventIds, inviteMap, userInvitedEventIds);
    }

    private Map<UUID, Integer> buildCountMap(List<UUID> slotIds) {
        if (slotIds.isEmpty()) return Map.of();
        Map<UUID, Integer> countMap = new HashMap<>(
            reservationRepository.countConfirmedByTimeSlotIds(slotIds).stream()
                .collect(Collectors.toMap(SlotParticipantCount::slotId, SlotParticipantCount::countAsInt))
        );
        guestReservationRepository.sumParticipantsByTimeSlotIds(slotIds)
            .forEach(g -> countMap.merge(g.slotId(), g.countAsInt(), Integer::sum));
        return countMap;
    }

    private Map<UUID, Integer> buildSlotInviteMap(List<UUID> slotIds) {
        if (slotIds.isEmpty()) return Map.of();
        return reservedSeatRepository.countPendingBySlotIds(slotIds).stream()
            .collect(Collectors.toMap(ReservedSeatCount::targetId, ReservedSeatCount::countAsInt));
    }
}
