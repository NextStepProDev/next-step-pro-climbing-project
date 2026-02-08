package pl.nextsteppro.climbing.api.reservation;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.ReservationStatus;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlotRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.domain.waitlist.WaitlistEntry;
import pl.nextsteppro.climbing.domain.waitlist.WaitlistRepository;
import pl.nextsteppro.climbing.infrastructure.mail.MailService;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;
    private final WaitlistRepository waitlistRepository;
    private final MailService mailService;

    public ReservationService(ReservationRepository reservationRepository,
                             TimeSlotRepository timeSlotRepository,
                             UserRepository userRepository,
                             WaitlistRepository waitlistRepository,
                             MailService mailService) {
        this.reservationRepository = reservationRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.userRepository = userRepository;
        this.waitlistRepository = waitlistRepository;
        this.mailService = mailService;
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public ReservationResultDto createReservation(UUID slotId, UUID userId, @Nullable String comment, int participants) {
        if (participants < 1) {
            throw new IllegalArgumentException("Liczba miejsc musi wynosić co najmniej 1");
        }

        TimeSlot slot = timeSlotRepository.findByIdForUpdate(slotId)
            .orElseThrow(() -> new IllegalArgumentException("Time slot not found"));

        LocalDateTime slotDateTime = LocalDateTime.of(slot.getDate(), slot.getStartTime());
        if (slotDateTime.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Nie można zarezerwować terminu, który już minął");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (slot.isBlocked()) {
            throw new IllegalStateException("This time slot is blocked");
        }

        if (reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slotId, ReservationStatus.CONFIRMED)) {
            throw new IllegalStateException("Masz już rezerwację na ten termin");
        }

        int currentCount = reservationRepository.countConfirmedByTimeSlotId(slotId);
        int spotsLeft = slot.getMaxParticipants() - currentCount;
        if (spotsLeft <= 0) {
            throw new IllegalStateException("Brak wolnych miejsc. Zapisz się na listę rezerwową.");
        }
        if (participants > spotsLeft) {
            throw new IllegalStateException("Dostępnych miejsc: " + spotsLeft + ". Nie można zarezerwować " + participants + ".");
        }

        Reservation reservation = new Reservation(user, slot);
        reservation.setParticipants(participants);
        if (comment != null && !comment.isBlank()) {
            reservation.setComment(comment.length() > 500 ? comment.substring(0, 500) : comment);
        }
        reservation = reservationRepository.save(reservation);

        mailService.sendReservationConfirmation(reservation);
        mailService.sendAdminNotification(reservation);

        return new ReservationResultDto(
            reservation.getId(),
            true,
            "Rezerwacja potwierdzona!"
        );
    }

    @Caching(evict = {
        @CacheEvict(value = "calendarMonth", allEntries = true),
        @CacheEvict(value = "calendarDay", allEntries = true)
    })
    public void cancelReservation(UUID reservationId, UUID userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new IllegalArgumentException("Reservation not found"));

        if (!reservation.getUser().getId().equals(userId)) {
            throw new IllegalStateException("You can only cancel your own reservations");
        }

        if (reservation.isCancelled()) {
            throw new IllegalStateException("This reservation is already cancelled");
        }

        reservation.cancel();
        reservationRepository.save(reservation);

        mailService.sendCancellationConfirmation(reservation);

        notifyNextOnWaitlist(reservation.getTimeSlot().getId());
    }

    @Transactional(readOnly = true)
    public List<UserReservationDto> getUserReservations(UUID userId) {
        return reservationRepository.findByUserId(userId).stream()
            .filter(Reservation::isConfirmed)
            .map(this::toUserReservationDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<UserReservationDto> getUserUpcomingReservations(UUID userId) {
        return reservationRepository.findUpcomingByUserId(userId, LocalDate.now()).stream()
            .map(this::toUserReservationDto)
            .toList();
    }

    public WaitlistResultDto joinWaitlist(UUID slotId, UUID userId) {
        TimeSlot slot = timeSlotRepository.findById(slotId)
            .orElseThrow(() -> new IllegalArgumentException("Time slot not found"));

        LocalDateTime slotDateTime = LocalDateTime.of(slot.getDate(), slot.getStartTime());
        if (slotDateTime.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Nie można zapisać się na listę rezerwową terminu, który już minął");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (waitlistRepository.existsByUserIdAndTimeSlotId(userId, slotId)) {
            throw new IllegalStateException("You are already on the waitlist");
        }

        if (reservationRepository.existsByUserIdAndTimeSlotIdAndStatus(userId, slotId, ReservationStatus.CONFIRMED)) {
            throw new IllegalStateException("You already have a confirmed reservation");
        }

        int position = waitlistRepository.findMaxPositionByTimeSlotId(slotId) + 1;
        WaitlistEntry entry = new WaitlistEntry(user, slot, position);
        entry = waitlistRepository.save(entry);

        return new WaitlistResultDto(
            entry.getId(),
            position,
            "Dodano do listy rezerwowej na pozycji " + position
        );
    }

    public void leaveWaitlist(UUID entryId, UUID userId) {
        WaitlistEntry entry = waitlistRepository.findById(entryId)
            .orElseThrow(() -> new IllegalArgumentException("Waitlist entry not found"));

        if (!entry.getUser().getId().equals(userId)) {
            throw new IllegalStateException("You can only leave your own waitlist entries");
        }

        int position = entry.getPosition();
        UUID slotId = entry.getTimeSlot().getId();

        waitlistRepository.delete(entry);
        waitlistRepository.decrementPositionsAfter(slotId, position);
    }

    private void notifyNextOnWaitlist(UUID slotId) {
        WaitlistEntry nextEntry = waitlistRepository.findFirstNotNotifiedByTimeSlotId(slotId);
        if (nextEntry != null) {
            nextEntry.markNotified();
            waitlistRepository.save(nextEntry);
            mailService.sendWaitlistNotification(nextEntry);
        }
    }

    private UserReservationDto toUserReservationDto(Reservation reservation) {
        TimeSlot slot = reservation.getTimeSlot();
        return new UserReservationDto(
            reservation.getId(),
            slot.getDate(),
            slot.getStartTime(),
            slot.getEndTime(),
            reservation.getStatus().name(),
            slot.getDisplayTitle(),
            reservation.getComment(),
            reservation.getParticipants(),
            reservation.getCreatedAt()
        );
    }
}
