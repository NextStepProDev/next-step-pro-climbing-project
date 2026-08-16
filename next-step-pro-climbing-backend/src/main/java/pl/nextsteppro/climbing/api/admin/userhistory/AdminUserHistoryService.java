package pl.nextsteppro.climbing.api.admin.userhistory;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogDto;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogService;
import pl.nextsteppro.climbing.domain.climbingascent.ClimbingAscentRepository;
import pl.nextsteppro.climbing.domain.course.Course;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTrainingRepository;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.reservedseat.ReservedSeat;
import pl.nextsteppro.climbing.domain.reservedseat.ReservedSeatRepository;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.trainingrequest.TrainingRequest;
import pl.nextsteppro.climbing.domain.trainingrequest.TrainingRequestRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.domain.waitlist.EventWaitlist;
import pl.nextsteppro.climbing.domain.waitlist.EventWaitlistRepository;
import pl.nextsteppro.climbing.domain.waitlist.Waitlist;
import pl.nextsteppro.climbing.domain.waitlist.WaitlistRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The admin's read-only card for one user: account state, headline counts, an activity timeline
 * and everything booking-shaped, assembled from repositories that already exist.
 *
 * <p><b>Read-only throughout.</b> Every existing write path stays where it is — in the user list
 * (role, athlete flag, forced logout, deletion) and in the coach's calendar. Same stance as the
 * coach's view of weigh-ins and the logbook: reading somebody's history is not a licence to edit it.
 *
 * <p><b>The athlete flag is a privacy boundary, not a display preference.</b> Training and logbook
 * numbers are omitted for anyone without it, because the endpoints behind the Training tab refuse
 * those users anyway ({@code requireFlaggedAthlete}) — a plain user's logbook is private, and being
 * read by a coach has to follow from a decision somebody made.
 */
@Service
public class AdminUserHistoryService {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    static final int DEFAULT_PAST_SIZE = 25;
    static final int MAX_PAST_SIZE = 100;

    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final WaitlistRepository waitlistRepository;
    private final EventWaitlistRepository eventWaitlistRepository;
    private final ReservedSeatRepository reservedSeatRepository;
    private final TrainingRequestRepository trainingRequestRepository;
    private final ActivityLogService activityLogService;
    private final PersonalTrainingRepository personalTrainingRepository;
    private final ClimbingAscentRepository climbingAscentRepository;

    public AdminUserHistoryService(UserRepository userRepository,
                                   ReservationRepository reservationRepository,
                                   WaitlistRepository waitlistRepository,
                                   EventWaitlistRepository eventWaitlistRepository,
                                   ReservedSeatRepository reservedSeatRepository,
                                   TrainingRequestRepository trainingRequestRepository,
                                   ActivityLogService activityLogService,
                                   PersonalTrainingRepository personalTrainingRepository,
                                   ClimbingAscentRepository climbingAscentRepository) {
        this.userRepository = userRepository;
        this.reservationRepository = reservationRepository;
        this.waitlistRepository = waitlistRepository;
        this.eventWaitlistRepository = eventWaitlistRepository;
        this.reservedSeatRepository = reservedSeatRepository;
        this.trainingRequestRepository = trainingRequestRepository;
        this.activityLogService = activityLogService;
        this.personalTrainingRepository = personalTrainingRepository;
        this.climbingAscentRepository = climbingAscentRepository;
    }

    @Transactional(readOnly = true)
    public Optional<UserDetailDto> getUserDetail(UUID userId) {
        return userRepository.findById(userId).map(this::toDetail);
    }

    @Transactional(readOnly = true)
    public Optional<List<ActivityLogDto>> getActivity(UUID userId, int page, int size) {
        if (!userRepository.existsById(userId)) return Optional.empty();
        return Optional.of(activityLogService.getLogsForUser(userId, page, Math.min(size, 100)));
    }

    /**
     * Only the past list is paged, and that is the whole point: it is the one section with no
     * ceiling — one row per attended session, one per DAY of a multi-day event, growing for as long
     * as the account exists. The others are bounded by construction: upcoming bookings, queue
     * entries and held seats only ever hold future or active rows, and proposals are capped at
     * three pending at a time. Paging a list that cannot exceed a handful buys nothing and costs a
     * control on screen.
     */
    @Transactional(readOnly = true)
    public Optional<UserReservationHistoryDto> getReservationHistory(UUID userId, int pastPage, int pastSize) {
        if (!userRepository.existsById(userId)) return Optional.empty();

        // Slot times in the database are Warsaw wall-clock, so "has it happened yet" is asked in
        // Warsaw. The container runs UTC — a bare now() splits the list two hours off in summer.
        LocalDateTime now = LocalDateTime.now(WARSAW);
        LocalDate today = now.toLocalDate();
        LocalTime time = now.toLocalTime();

        List<HistoryReservationDto> upcoming =
            reservationRepository.findUpcomingByUserIdIncludingAdminCancelled(userId, today, time)
                .stream().map(AdminUserHistoryService::toReservationDto).toList();

        Pageable pastPageable = PageRequest.of(Math.max(0, pastPage), clampPastSize(pastSize));
        List<HistoryReservationDto> past =
            reservationRepository.findPastByUserId(userId, today, time, pastPageable)
                .stream().map(AdminUserHistoryService::toReservationDto).toList();
        long pastTotal = reservationRepository.countPastByUserId(userId, today, time);

        List<HistoryWaitlistDto> waitlist = java.util.stream.Stream.concat(
            waitlistRepository.findActiveByUserId(userId).stream()
                .map(AdminUserHistoryService::toWaitlistDto),
            eventWaitlistRepository.findActiveByUserId(userId).stream()
                .map(AdminUserHistoryService::toEventWaitlistDto)
        ).toList();

        List<HistoryInviteDto> invitations = java.util.stream.Stream.concat(
            reservedSeatRepository.findUpcomingPendingSlotInvitesByUserId(userId, today, time).stream()
                .map(AdminUserHistoryService::toSlotInviteDto),
            reservedSeatRepository.findUpcomingPendingEventInvitesByUserId(userId, today).stream()
                .map(AdminUserHistoryService::toEventInviteDto)
        ).toList();

        List<HistoryRequestDto> requests = trainingRequestRepository.findByUserIdWithDetails(userId)
            .stream().map(AdminUserHistoryService::toRequestDto).toList();

        return Optional.of(new UserReservationHistoryDto(
            upcoming, past, pastTotal, pastPageable.getPageNumber(), pastPageable.getPageSize(),
            waitlist, invitations, requests));
    }

    /** A page size the client asks for, kept inside a range the server is willing to render. */
    private static int clampPastSize(int requested) {
        if (requested < 1) return DEFAULT_PAST_SIZE;
        return Math.min(requested, MAX_PAST_SIZE);
    }

    private UserDetailDto toDetail(User user) {
        UUID id = user.getId();
        // Null, not zero, for non-athletes: the tiles must not claim "0 trainings" about somebody
        // whose calendar this admin is not allowed to read in the first place.
        Long trainings = user.isAthlete() ? personalTrainingRepository.countCompletedTrainings(id) : null;
        Long ascents = user.isAthlete() ? climbingAscentRepository.countByAthleteId(id) : null;

        UserCountsDto counts = new UserCountsDto(
            reservationRepository.countConfirmedByUserId(id),
            reservationRepository.countCancelledByUserId(id),
            trainings,
            ascents);

        return new UserDetailDto(
            id,
            user.getFirstName(),
            user.getLastName(),
            user.getNickname(),
            user.getEmail(),
            user.getPhone(),
            avatarUrl(user),
            user.getRole().name(),
            user.isAthlete(),
            user.isEmailVerified(),
            user.getEmailVerifiedAt(),
            user.hasPassword(),
            user.getOauthProvider(),
            user.getPreferredLanguage(),
            user.isEmailNotificationsEnabled(),
            user.isNewsletterSubscribed(),
            user.isNewsletterChoiceMade(),
            user.getNewsletterSubscribedAt(),
            user.isAscentsPublic(),
            user.getTrainingConsentAt(),
            user.getFailedLoginAttempts(),
            user.getLockedUntil(),
            user.isAccountLocked(),
            user.getCreatedAt(),
            user.getUpdatedAt(),
            counts);
    }

    private static HistoryReservationDto toReservationDto(Reservation reservation) {
        TimeSlot slot = reservation.getTimeSlot();
        Event event = slot.getEvent();
        return new HistoryReservationDto(
            reservation.getId(),
            slot.getId(),
            event != null ? event.getId() : null,
            slot.getDate(),
            slot.getStartTime(),
            slot.getEndTime(),
            slot.getDisplayTitle(),
            event != null ? event.getTitle() : null,
            reservation.getStatus().name(),
            reservation.getParticipants(),
            reservation.getComment(),
            reservation.isCreatedByAdmin(),
            reservation.getCreatedAt());
    }

    private static HistoryWaitlistDto toWaitlistDto(Waitlist entry) {
        TimeSlot slot = entry.getTimeSlot();
        return new HistoryWaitlistDto(
            entry.getId(),
            "SLOT",
            slot.getId(),
            slot.getDisplayTitle(),
            slot.getDate(),
            slot.getStartTime(),
            entry.getPosition(),
            entry.getStatus().name(),
            entry.getConfirmationDeadline(),
            entry.getCreatedAt());
    }

    private static HistoryWaitlistDto toEventWaitlistDto(EventWaitlist entry) {
        Event event = entry.getEvent();
        return new HistoryWaitlistDto(
            entry.getId(),
            "EVENT",
            event.getId(),
            event.getTitle(),
            event.getStartDate(),
            event.getStartTime(),
            entry.getPosition(),
            entry.getStatus().name(),
            entry.getConfirmationDeadline(),
            entry.getCreatedAt());
    }

    private static HistoryInviteDto toSlotInviteDto(ReservedSeat seat) {
        TimeSlot slot = seat.getTimeSlot();
        return new HistoryInviteDto(
            seat.getId(),
            "SLOT",
            slot.getId(),
            slot.getDisplayTitle(),
            slot.getDate(),
            slot.getStartTime(),
            seat.getNotifiedAt(),
            seat.getCreatedAt());
    }

    private static HistoryInviteDto toEventInviteDto(ReservedSeat seat) {
        Event event = seat.getEvent();
        return new HistoryInviteDto(
            seat.getId(),
            "EVENT",
            event.getId(),
            event.getTitle(),
            event.getStartDate(),
            event.getStartTime(),
            seat.getNotifiedAt(),
            seat.getCreatedAt());
    }

    private static HistoryRequestDto toRequestDto(TrainingRequest request) {
        Course course = request.getCourse();
        return new HistoryRequestDto(
            request.getId(),
            request.getRequestedDate(),
            request.getStartTime(),
            request.getEndTime(),
            request.getParticipants(),
            request.getComment(),
            request.getStatus().name(),
            request.getAdminNote(),
            course != null ? course.getTitle() : null,
            request.getResolvedAt(),
            request.getCreatedAt());
    }

    @Nullable
    private static String avatarUrl(User user) {
        return user.getAvatarFilename() != null
            ? "/api/files/avatars/" + user.getAvatarFilename()
            : null;
    }
}
