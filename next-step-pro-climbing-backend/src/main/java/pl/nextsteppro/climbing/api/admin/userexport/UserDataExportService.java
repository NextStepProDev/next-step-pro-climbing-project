package pl.nextsteppro.climbing.api.admin.userexport;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import pl.nextsteppro.climbing.api.activitylog.ActivityLogService;
import pl.nextsteppro.climbing.domain.activitylog.ActivityLog;
import pl.nextsteppro.climbing.domain.activitylog.ActivityLogRepository;
import pl.nextsteppro.climbing.domain.athletegoal.AthleteGoal;
import pl.nextsteppro.climbing.domain.athletegoal.AthleteGoalRepository;
import pl.nextsteppro.climbing.domain.athleteweight.AthleteWeightRepository;
import pl.nextsteppro.climbing.domain.climbingascent.ClimbingAscent;
import pl.nextsteppro.climbing.domain.climbingascent.ClimbingAscentRepository;
import pl.nextsteppro.climbing.domain.course.Course;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.news.NewsStarRepository;
import pl.nextsteppro.climbing.domain.newsletter.NewsletterConsentLogRepository;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTraining;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTrainingRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingAttachment;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingAttachmentRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingComment;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingCommentFile;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingCommentFileRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingCommentRepository;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.ReservationRpe;
import pl.nextsteppro.climbing.domain.reservation.ReservationRpeRepository;
import pl.nextsteppro.climbing.domain.reservedseat.ReservedSeat;
import pl.nextsteppro.climbing.domain.reservedseat.ReservedSeatRepository;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.reschedule.RescheduleRequest;
import pl.nextsteppro.climbing.domain.reschedule.RescheduleRequestRepository;
import pl.nextsteppro.climbing.domain.trainingrequest.TrainingRequest;
import pl.nextsteppro.climbing.domain.trainingrequest.TrainingRequestRepository;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.domain.waitlist.EventWaitlist;
import pl.nextsteppro.climbing.domain.waitlist.EventWaitlistRepository;
import pl.nextsteppro.climbing.domain.waitlist.Waitlist;
import pl.nextsteppro.climbing.domain.waitlist.WaitlistRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Everything held about one person, in one file, for a GDPR access or portability request
 * (art. 15 / 20). The privacy policy promises both "on request"; this is what answers the request
 * without hand-written SQL against production.
 *
 * <p><b>No athlete-flag or consent gate, unlike every other read of the training data.</b> Those
 * gates decide whether the coach may keep WORKING with the data. The right of access is the
 * person's, and it does not lapse because the flag was revoked — revoking it keeps the plan rows,
 * so a gate here would hide exactly the data somebody most plausibly asks about.
 *
 * <p><b>Left out on purpose:</b> the admin's private notes (the owner's notebook, not data about
 * this person that the person supplied — and isolated by {@code PrivateNoteIsolationTest}), the
 * password hash, the OAuth id, the newsletter unsubscribe token, login lockout counters, read
 * markers, and the coach-only rapid-loss flag. Money comes from the settlement endpoint; see
 * {@link UserDataExportDto}.
 *
 * <p>Not read-only: handing out somebody's data is itself logged, under the admin who did it.
 */
@Service
public class UserDataExportService {

    // 2: rescheduleRequests section added (2026-10-21)
    static final int SCHEMA_VERSION = 2;

    private final UserRepository userRepository;
    private final NewsletterConsentLogRepository consentLogRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationRpeRepository rpeRepository;
    private final WaitlistRepository waitlistRepository;
    private final EventWaitlistRepository eventWaitlistRepository;
    private final ReservedSeatRepository reservedSeatRepository;
    private final TrainingRequestRepository trainingRequestRepository;
    private final RescheduleRequestRepository rescheduleRequestRepository;
    private final NewsStarRepository newsStarRepository;
    private final ActivityLogRepository activityLogRepository;
    private final PersonalTrainingRepository trainingRepository;
    private final TrainingAttachmentRepository attachmentRepository;
    private final TrainingCommentRepository commentRepository;
    private final TrainingCommentFileRepository commentFileRepository;
    private final AthleteGoalRepository goalRepository;
    private final AthleteWeightRepository weightRepository;
    private final ClimbingAscentRepository ascentRepository;
    private final ActivityLogService activityLogService;

    public UserDataExportService(UserRepository userRepository,
                                 NewsletterConsentLogRepository consentLogRepository,
                                 ReservationRepository reservationRepository,
                                 ReservationRpeRepository rpeRepository,
                                 WaitlistRepository waitlistRepository,
                                 EventWaitlistRepository eventWaitlistRepository,
                                 ReservedSeatRepository reservedSeatRepository,
                                 TrainingRequestRepository trainingRequestRepository,
                                 NewsStarRepository newsStarRepository,
                                 ActivityLogRepository activityLogRepository,
                                 PersonalTrainingRepository trainingRepository,
                                 TrainingAttachmentRepository attachmentRepository,
                                 TrainingCommentRepository commentRepository,
                                 TrainingCommentFileRepository commentFileRepository,
                                 AthleteGoalRepository goalRepository,
                                 AthleteWeightRepository weightRepository,
                                 ClimbingAscentRepository ascentRepository,
                                 ActivityLogService activityLogService,
                                 RescheduleRequestRepository rescheduleRequestRepository) {
        this.rescheduleRequestRepository = rescheduleRequestRepository;
        this.userRepository = userRepository;
        this.consentLogRepository = consentLogRepository;
        this.reservationRepository = reservationRepository;
        this.rpeRepository = rpeRepository;
        this.waitlistRepository = waitlistRepository;
        this.eventWaitlistRepository = eventWaitlistRepository;
        this.reservedSeatRepository = reservedSeatRepository;
        this.trainingRequestRepository = trainingRequestRepository;
        this.newsStarRepository = newsStarRepository;
        this.activityLogRepository = activityLogRepository;
        this.trainingRepository = trainingRepository;
        this.attachmentRepository = attachmentRepository;
        this.commentRepository = commentRepository;
        this.commentFileRepository = commentFileRepository;
        this.goalRepository = goalRepository;
        this.weightRepository = weightRepository;
        this.ascentRepository = ascentRepository;
        this.activityLogService = activityLogService;
    }

    @Transactional
    public Optional<UserDataExportDto> export(UUID userId, UUID adminId) {
        Optional<User> found = userRepository.findById(userId);
        if (found.isEmpty()) return Optional.empty();
        User user = found.get();

        UserDataExportDto dto = new UserDataExportDto(
            SCHEMA_VERSION,
            Instant.now(),
            toAccount(user),
            consentLogRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(e -> new ExportConsentEventDto(e.getAction(), e.getSource(), e.getCreatedAt()))
                .toList(),
            reservations(userId),
            waitlists(userId),
            reservedSeatRepository.findAllByUserIdWithTarget(userId).stream()
                .map(UserDataExportService::toInvitation).toList(),
            trainingRequestRepository.findByUserIdWithDetails(userId).stream()
                .map(UserDataExportService::toRequest).toList(),
            rescheduleRequestRepository.findAllByUserId(userId).stream()
                .map(UserDataExportService::toReschedule).toList(),
            newsStarRepository.findStarredRows(userId).stream()
                .map(row -> new ExportStarredNewsDto(row.newsId(), row.title(), row.starredAt()))
                .toList(),
            activityLogRepository.findByUserIdWithDetails(userId, Pageable.unpaged()).stream()
                // ⚠️ ADMIN_* entries are filed under the acting admin but describe actions on
                // OTHER people, by name and address. Exporting an admin must not hand those out.
                .filter(log -> !log.getActionType().name().startsWith("ADMIN_"))
                .map(UserDataExportService::toActivity).toList(),
            trainingPlan(userId),
            messages(userId),
            goalRepository.findByAthleteIdOrderByCreatedAtAsc(userId).stream()
                .map(UserDataExportService::toGoal).toList(),
            weightRepository.findByAthleteIdOrderByMeasuredOnAsc(userId).stream()
                .map(w -> new ExportWeightDto(w.getMeasuredOn(), w.getWeightKg(), w.getCreatedAt()))
                .toList(),
            ascentRepository.findByAthleteIdOrderByClimbedOnAscCreatedAtAsc(userId).stream()
                .map(UserDataExportService::toAscent).toList());

        // Logged after the reads, so the export's own entry is not part of the file it describes.
        User admin = userRepository.findById(adminId).orElseThrow();
        activityLogService.logAdminUserDataExported(admin, user);
        return Optional.of(dto);
    }

    private static ExportAccountDto toAccount(User user) {
        return new ExportAccountDto(
            user.getId(),
            user.getFirstName(),
            user.getLastName(),
            user.getNickname(),
            user.getEmail(),
            user.getPhone(),
            user.getAvatarFilename(),
            user.getRole().name(),
            user.getPreferredLanguage(),
            user.hasPassword(),
            user.getOauthProvider(),
            user.getEmailVerifiedAt(),
            user.isEmailNotificationsEnabled(),
            user.isNewsletterSubscribed(),
            user.getNewsletterSubscribedAt(),
            user.isAscentsPublic(),
            user.isAthlete(),
            user.getTrainingConsentAt(),
            user.getCreatedAt(),
            user.getUpdatedAt());
    }

    private List<ExportReservationDto> reservations(UUID userId) {
        List<Reservation> rows = reservationRepository.findAllByUserIdWithTarget(userId);
        if (rows.isEmpty()) return List.of();
        Map<UUID, ReservationRpe> rpeByReservation = rpeRepository
            .findByReservationIdIn(rows.stream().map(Reservation::getId).toList()).stream()
            .collect(Collectors.toMap(ReservationRpe::reservationId, Function.identity()));
        return rows.stream().map(r -> {
            TimeSlot slot = r.getTimeSlot();
            Event event = slot.getEvent();
            ReservationRpe rpe = rpeByReservation.get(r.getId());
            return new ExportReservationDto(
                r.getId(),
                slot.getId(),
                event != null ? event.getId() : null,
                slot.getDate(),
                slot.getStartTime(),
                slot.getEndTime(),
                slot.getDisplayTitle(),
                r.getStatus().name(),
                r.getParticipants(),
                plainOrNull(r.getComment()),
                r.isCreatedByAdmin(),
                rpe != null ? rpe.getRpe() : null,
                rpe != null ? plainOrNull(rpe.getNote()) : null,
                r.getCreatedAt());
        }).toList();
    }

    private List<ExportWaitlistDto> waitlists(UUID userId) {
        return Stream.concat(
            waitlistRepository.findAllByUserIdWithSlot(userId).stream()
                .map(UserDataExportService::toSlotWaitlist),
            eventWaitlistRepository.findAllByUserIdWithEvent(userId).stream()
                .map(UserDataExportService::toEventWaitlist)
        ).toList();
    }

    private static ExportWaitlistDto toSlotWaitlist(Waitlist entry) {
        TimeSlot slot = entry.getTimeSlot();
        return new ExportWaitlistDto("SLOT", slot.getId(), slot.getDisplayTitle(), slot.getDate(),
            slot.getStartTime(), entry.getPosition(), entry.getStatus().name(), entry.getCreatedAt());
    }

    private static ExportWaitlistDto toEventWaitlist(EventWaitlist entry) {
        Event event = entry.getEvent();
        return new ExportWaitlistDto("EVENT", event.getId(), event.getTitle(), event.getStartDate(),
            event.getStartTime(), entry.getPosition(), entry.getStatus().name(), entry.getCreatedAt());
    }

    private static ExportInvitationDto toInvitation(ReservedSeat seat) {
        TimeSlot slot = seat.getTimeSlot();
        if (slot != null) {
            return new ExportInvitationDto("SLOT", slot.getId(), slot.getDisplayTitle(), slot.getDate(),
                slot.getStartTime(), seat.getNotifiedAt(), seat.getCreatedAt());
        }
        Event event = seat.getEvent();
        return new ExportInvitationDto("EVENT", event.getId(), event.getTitle(), event.getStartDate(),
            event.getStartTime(), seat.getNotifiedAt(), seat.getCreatedAt());
    }

    private static ExportTrainingRequestDto toRequest(TrainingRequest request) {
        Course course = request.getCourse();
        return new ExportTrainingRequestDto(
            request.getId(),
            request.getRequestedDate(),
            request.getStartTime(),
            request.getEndTime(),
            request.getParticipants(),
            plainOrNull(request.getComment()),
            request.getStatus().name(),
            request.getAdminNote(),
            course != null ? course.getTitle() : null,
            request.getResolvedAt(),
            request.getCreatedAt());
    }

    private static ExportRescheduleRequestDto toReschedule(RescheduleRequest request) {
        TimeSlot slot = request.getTimeSlot();
        Event event = request.getEvent();
        return new ExportRescheduleRequestDto(
            request.getId(),
            slot != null ? "SLOT" : "EVENT",
            slot != null ? slot.getDisplayTitle() : event != null ? event.getTitle() : null,
            request.oldTerm().label(),
            request.newTerm().label(),
            plainOrNull(request.getComment()),
            request.getStatus().name(),
            request.getAdminNote(),
            request.getResolvedAt(),
            request.getCreatedAt());
    }

    private static ExportActivityDto toActivity(ActivityLog log) {
        TimeSlot slot = log.getTimeSlot();
        Event event = log.getEvent();
        return new ExportActivityDto(
            log.getActionType().name(),
            slot != null ? slot.getId() : null,
            event != null ? event.getId() : null,
            log.getParticipants(),
            log.getDescription(),
            log.getCreatedAt());
    }

    private List<ExportTrainingDto> trainingPlan(UUID userId) {
        List<PersonalTraining> trainings = trainingRepository.findByAthleteIdOrderByTrainingDateAscStartTimeAsc(userId);
        if (trainings.isEmpty()) return List.of();
        Map<UUID, List<ExportFileDto>> materials = attachmentRepository
            .findByTrainingIdInOrderByPositionAsc(trainings.stream().map(PersonalTraining::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(a -> a.getTraining().getId(),
                Collectors.mapping(UserDataExportService::toMaterial, Collectors.toList())));
        return trainings.stream().map(t -> new ExportTrainingDto(
            t.getId(),
            t.getKind().name(),
            t.getTrainingDate(),
            t.getStartTime(),
            t.getEndTime(),
            plain(t.getTitle()),
            plainOrNull(t.getDescription()),
            t.getTargetCalories(),
            t.isCreatedByAdmin(),
            t.getCompletedAt(),
            t.getRpe(),
            plainOrNull(t.getFeedback()),
            materials.getOrDefault(t.getId(), List.of()),
            t.getCreatedAt(),
            t.getUpdatedAt())).toList();
    }

    private static ExportFileDto toMaterial(TrainingAttachment a) {
        boolean link = a.getUrl() != null;
        return new ExportFileDto(
            a.getKind().name(),
            a.getUrl(),
            plainOrNull(link ? a.getLabel() : a.getOriginalName()),
            a.getMimeType(),
            a.getSizeBytes(),
            a.getExpiresAt(),
            a.getCreatedAt());
    }

    private List<ExportCommentDto> messages(UUID userId) {
        List<TrainingComment> comments = commentRepository.findByAthleteIdOrderByCreatedAtAsc(userId);
        if (comments.isEmpty()) return List.of();
        Map<UUID, List<ExportFileDto>> files = commentFileRepository
            .findByCommentIdInOrderByPositionAsc(comments.stream().map(TrainingComment::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(f -> f.getComment().getId(),
                Collectors.mapping(UserDataExportService::toCommentFile, Collectors.toList())));
        return comments.stream().map(c -> new ExportCommentDto(
            c.getId(),
            // getId() on the proxy reads the FK without loading the coach's account.
            c.getAuthor().getId().equals(userId) ? "ME" : "COACH",
            c.trainingId(),
            c.timeSlotId(),
            c.eventId(),
            plainOrNull(c.getBody()),
            files.getOrDefault(c.getId(), List.of()),
            c.getCreatedAt(),
            c.getEditedAt())).toList();
    }

    private static ExportFileDto toCommentFile(TrainingCommentFile f) {
        return new ExportFileDto("FILE", null, plainOrNull(f.getOriginalName()), f.getMimeType(), f.getSizeBytes(),
            f.getExpiresAt(), f.getCreatedAt());
    }

    private static ExportGoalDto toGoal(AthleteGoal goal) {
        return new ExportGoalDto(
            goal.getKind().name(),
            goal.getHorizon().name(),
            plainOrNull(goal.getContent()),
            goal.getStartWeightKg(),
            goal.getTargetWeightKg(),
            goal.getTargetDate(),
            goal.getAchievedAt(),
            goal.isAchievedAutomatically(),
            goal.getCreatedAt());
    }

    private static ExportAscentDto toAscent(ClimbingAscent a) {
        return new ExportAscentDto(
            a.getClimbedOn(),
            a.getTerrain().name(),
            enumName(a.getDiscipline()),
            a.getGrade().label(),
            a.getOriginalGrade(),
            a.getStyle().name(),
            a.getWinter(),
            a.getLengthMeters(),
            a.getPitches(),
            a.getDurationMinutes(),
            a.getLedGrade() != null ? a.getLedGrade().label() : null,
            a.getLedPitches(),
            a.getPartners(),
            a.getArea(),
            a.getCrag(),
            a.getRouteName(),
            a.getAttempts(),
            a.getQualityStars(),
            a.getComment(),
            a.getHiddenFromPublicAt() != null,
            a.getCreatedAt());
    }

    /**
     * Free text as the person typed it. These columns are stored HTML-escaped
     * ({@code StoredText.escapeAndCap}), and a file read in a text editor must not say
     * {@code &quot;4x8&quot;}. ⚠️ Only for columns that ARE escaped: unescaping a plain one (the
     * admin's rejection note, titles of sessions) would turn a literal "&amp;amp;" into "&amp;".
     */
    private static String plain(String text) {
        return HtmlUtils.htmlUnescape(text);
    }

    private static @Nullable String plainOrNull(@Nullable String text) {
        return text == null ? null : plain(text);
    }

    private static @Nullable String enumName(@Nullable Enum<?> value) {
        return value != null ? value.name() : null;
    }
}
