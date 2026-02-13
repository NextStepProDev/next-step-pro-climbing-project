package pl.nextsteppro.climbing.api.calendar;

import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventRepository;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.ReservationStatus;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlotRepository;

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
    private final EventRepository eventRepository;

    public CalendarService(TimeSlotRepository timeSlotRepository,
                          ReservationRepository reservationRepository,
                          EventRepository eventRepository) {
        this.timeSlotRepository = timeSlotRepository;
        this.reservationRepository = reservationRepository;
        this.eventRepository = eventRepository;
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

        // Batch: load user's confirmed slot IDs at once
        Set<UUID> userConfirmedSlotIds = userId != null && !allSlotIds.isEmpty()
            ? new HashSet<>(reservationRepository.findUserConfirmedSlotIds(userId, allSlotIds))
            : Set.of();

        Map<LocalDate, List<TimeSlot>> slotsByDate = slots.stream()
            .collect(Collectors.groupingBy(TimeSlot::getDate));

        List<DaySummaryDto> days = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            List<TimeSlot> daySlots = slotsByDate.getOrDefault(date, List.of());
            days.add(createDaySummary(date, daySlots, countMap, userConfirmedSlotIds));
        }

        EventData eventData = computeEventData(events, userId);

        List<EventSummaryDto> eventSummaries = events.stream()
            .map(event -> toEventSummary(event, eventData.participantsMap().getOrDefault(event.getId(), 0),
                                         eventData.userRegisteredEventIds().contains(event.getId())))
            .toList();

        return new MonthViewDto(yearMonth.toString(), days, eventSummaries);
    }

    public DayViewDto getDayView(LocalDate date, @Nullable UUID userId) {
        List<TimeSlot> slots = timeSlotRepository.findByDateSorted(date);
        List<Event> events = eventRepository.findActiveEventsOnDate(date);

        List<UUID> slotIds = slots.stream().map(TimeSlot::getId).toList();
        Map<UUID, Integer> countMap = buildCountMap(slotIds);
        Set<UUID> userConfirmedSlotIds = userId != null && !slotIds.isEmpty()
            ? new HashSet<>(reservationRepository.findUserConfirmedSlotIds(userId, slotIds))
            : Set.of();

        List<TimeSlotDto> slotDtos = slots.stream()
            .filter(slot -> !slot.belongsToEvent())
            .map(slot -> toTimeSlotDto(slot, countMap.getOrDefault(slot.getId(), 0),
                                       userConfirmedSlotIds.contains(slot.getId())))
            .toList();

        EventData eventData = computeEventData(events, userId);

        List<EventSummaryDto> eventSummaries = events.stream()
            .map(event -> toEventSummary(event, eventData.participantsMap().getOrDefault(event.getId(), 0),
                                         eventData.userRegisteredEventIds().contains(event.getId())))
            .toList();

        return new DayViewDto(date, slotDtos, eventSummaries);
    }

    public EventSummaryDto getEventSummary(UUID eventId, @Nullable UUID userId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));

        EventData eventData = computeEventData(List.of(event), userId);
        int currentParticipants = eventData.participantsMap().getOrDefault(event.getId(), 0);
        boolean isUserRegistered = eventData.userRegisteredEventIds().contains(event.getId());

        return toEventSummary(event, currentParticipants, isUserRegistered);
    }

    public TimeSlotDetailDto getSlotDetails(UUID slotId, @Nullable UUID userId) {
        TimeSlot slot = timeSlotRepository.findById(slotId)
            .orElseThrow(() -> new IllegalArgumentException("Time slot not found: " + slotId));

        int confirmedCount = reservationRepository.countConfirmedByTimeSlotId(slotId);

        boolean isUserRegistered = false;
        @Nullable UUID reservationId = null;

        if (userId != null) {
            Reservation reservation = reservationRepository.findByUserIdAndTimeSlotId(userId, slotId);
            if (reservation != null && reservation.getStatus() == ReservationStatus.CONFIRMED) {
                isUserRegistered = true;
                reservationId = reservation.getId();
            }
        }

        SlotStatus status = determineSlotStatus(slot, confirmedCount);

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
            reservationId
        );
    }

    private DaySummaryDto createDaySummary(LocalDate date, List<TimeSlot> slots,
                                          Map<UUID, Integer> countMap, Set<UUID> userConfirmedSlotIds) {
        List<TimeSlot> standaloneSlots = slots.stream()
            .filter(slot -> !slot.belongsToEvent())
            .toList();

        int totalSlots = standaloneSlots.size();
        int availableSlots = 0;
        boolean hasUserReservation = false;

        LocalDateTime cutoff = LocalDateTime.now().plusHours(BOOKING_CUTOFF_HOURS);
        for (TimeSlot slot : standaloneSlots) {
            LocalDateTime slotDateTime = LocalDateTime.of(slot.getDate(), slot.getStartTime());
            if (!slot.isBlocked() && slotDateTime.isAfter(cutoff)) {
                int confirmed = countMap.getOrDefault(slot.getId(), 0);
                if (confirmed < slot.getMaxParticipants()) {
                    availableSlots++;
                }
            }
            if (userConfirmedSlotIds.contains(slot.getId())) {
                hasUserReservation = true;
            }
        }

        return new DaySummaryDto(date, totalSlots, availableSlots, hasUserReservation);
    }

    private TimeSlotDto toTimeSlotDto(TimeSlot slot, int confirmedCount, boolean isUserRegistered) {
        SlotStatus status = determineSlotStatus(slot, confirmedCount);

        return new TimeSlotDto(
            slot.getId(),
            slot.getStartTime(),
            slot.getEndTime(),
            slot.getMaxParticipants(),
            status,
            isUserRegistered,
            slot.getDisplayTitle()
        );
    }

    private static final int BOOKING_CUTOFF_HOURS = 12;

    private SlotStatus determineSlotStatus(TimeSlot slot, int confirmedCount) {
        LocalDateTime slotDateTime = LocalDateTime.of(slot.getDate(), slot.getStartTime());
        LocalDateTime now = LocalDateTime.now();
        if (slotDateTime.isBefore(now)) {
            return SlotStatus.PAST;
        }
        if (slot.isBlocked()) {
            return SlotStatus.BLOCKED;
        }
        if (confirmedCount >= slot.getMaxParticipants()) {
            return SlotStatus.FULL;
        }
        if (slotDateTime.isBefore(now.plusHours(BOOKING_CUTOFF_HOURS))) {
            return SlotStatus.BOOKING_CLOSED;
        }
        return SlotStatus.AVAILABLE;
    }

    private EventSummaryDto toEventSummary(Event event, int currentParticipants, boolean isUserRegistered) {
        LocalDateTime eventStart = LocalDateTime.of(
            event.getStartDate(),
            event.getStartTime() != null ? event.getStartTime() : LocalTime.of(0, 0)
        );
        boolean enrollmentOpen = eventStart.isAfter(LocalDateTime.now().plusHours(BOOKING_CUTOFF_HOURS));
        return new EventSummaryDto(
            event.getId(),
            event.getTitle(),
            event.getDescription(),
            event.getLocation(),
            event.getEventType().name(),
            event.getStartDate(),
            event.getEndDate(),
            event.isMultiDay(),
            event.getMaxParticipants(),
            currentParticipants,
            isUserRegistered,
            enrollmentOpen
        );
    }

    private record EventData(Map<UUID, Integer> participantsMap, Set<UUID> userRegisteredEventIds) {}

    private EventData computeEventData(List<Event> events, @Nullable UUID userId) {
        if (events.isEmpty()) {
            return new EventData(Map.of(), Set.of());
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

        return new EventData(participantsMap, userRegisteredEventIds);
    }

    private Map<UUID, Integer> buildCountMap(List<UUID> slotIds) {
        if (slotIds.isEmpty()) return Map.of();
        return reservationRepository.countConfirmedByTimeSlotIds(slotIds).stream()
            .collect(Collectors.toMap(
                row -> (UUID) row[0],
                row -> ((Number) row[1]).intValue()
            ));
    }
}
