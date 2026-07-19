package pl.nextsteppro.climbing.api.trainingcalendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.nextsteppro.climbing.domain.personaltraining.AthleteActivityCount;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTraining;
import pl.nextsteppro.climbing.domain.personaltraining.PersonalTrainingRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingAttachment;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingAttachmentRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingCalendarRead;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingCalendarReadRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingComment;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingCommentRepository;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingDeletion;
import pl.nextsteppro.climbing.domain.personaltraining.TrainingDeletionRepository;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.reservation.ReservationRepository;
import pl.nextsteppro.climbing.domain.reservedseat.ReservedSeat;
import pl.nextsteppro.climbing.domain.reservedseat.ReservedSeatRepository;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRepository;
import pl.nextsteppro.climbing.infrastructure.i18n.MessageService;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TrainingCalendarService.
 * Verifies: athlete guard, ownership guard, time validation, completion, status derivation,
 * unread counters (coach activity counts, own does not), seen-marker upsert.
 */
@ExtendWith(MockitoExtension.class)
class TrainingCalendarServiceTest {

    @Mock private PersonalTrainingRepository trainingRepository;
    @Mock private TrainingCommentRepository commentRepository;
    @Mock private TrainingCalendarReadRepository readRepository;
    @Mock private TrainingDeletionRepository deletionRepository;
    @Mock private TrainingAttachmentRepository attachmentRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private ReservedSeatRepository reservedSeatRepository;
    @Mock private UserRepository userRepository;
    @Mock private MessageService msg;

    private TrainingCalendarService service;

    private UUID athleteId;
    private User athlete;

    @BeforeEach
    void setUp() {
        service = new TrainingCalendarService(
            trainingRepository, commentRepository, readRepository, deletionRepository,
            attachmentRepository, reservationRepository, reservedSeatRepository, userRepository, msg);

        lenient().when(msg.get(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(msg.get(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
        // Single-training DTO paths load attachments; default to none
        lenient().when(attachmentRepository.findByTrainingIdOrderByPositionAsc(any())).thenReturn(java.util.List.of());

        athleteId = UUID.randomUUID();
        athlete = buildAthlete(athleteId);
        lenient().when(userRepository.findById(athleteId)).thenReturn(Optional.of(athlete));
    }

    // ========== create ==========

    @Test
    void shouldCreateTrainingWithSanitizedTitleWhenValid() {
        // Given
        when(trainingRepository.save(any())).thenAnswer(inv -> {
            PersonalTraining t = inv.getArgument(0);
            setField(t, "createdAt", Instant.now());
            return t;
        });
        CreatePersonalTrainingRequest request = new CreatePersonalTrainingRequest(
            LocalDate.now().plusDays(2), LocalTime.of(18, 0), LocalTime.of(19, 30),
            "Trening <b>siłowy</b>", "Kampus + zwisy");

        // When
        PersonalTrainingDto dto = service.createMy(athleteId, request);

        // Then
        ArgumentCaptor<PersonalTraining> captor = ArgumentCaptor.forClass(PersonalTraining.class);
        verify(trainingRepository).save(captor.capture());
        PersonalTraining saved = captor.getValue();
        assertEquals("Trening &lt;b&gt;siłowy&lt;/b&gt;", saved.getTitle());
        assertFalse(saved.isCreatedByAdmin());
        assertFalse(saved.isLastModifiedByAdmin());
        assertEquals("PLANNED", dto.status());
    }

    @Test
    void shouldAllowCreatingTrainingOnPastDate() {
        // Retroactive logging is a core workflow — past dates must NOT be rejected
        when(trainingRepository.save(any())).thenAnswer(inv -> {
            PersonalTraining t = inv.getArgument(0);
            setField(t, "createdAt", Instant.now());
            return t;
        });
        CreatePersonalTrainingRequest request = new CreatePersonalTrainingRequest(
            LocalDate.now().minusDays(5), LocalTime.of(18, 0), LocalTime.of(19, 30), "Zaległy", null);

        PersonalTrainingDto dto = service.createMy(athleteId, request);

        assertEquals("MISSED", dto.status(), "past uncompleted entry is derived as missed");
    }

    @Test
    void shouldRejectCreateWhenEndNotAfterStart() {
        CreatePersonalTrainingRequest request = new CreatePersonalTrainingRequest(
            LocalDate.now().plusDays(2), LocalTime.of(18, 0), LocalTime.of(18, 0), "Trening", null);

        assertThrows(IllegalArgumentException.class, () -> service.createMy(athleteId, request));
        verify(trainingRepository, never()).save(any());
    }

    @Test
    void shouldRejectCreateWhenUserIsNotAthlete() {
        UUID regularId = UUID.randomUUID();
        User regular = new User("user@example.com", "Jan", "Kowalski", "+48123456789", "jan");
        when(userRepository.findById(regularId)).thenReturn(Optional.of(regular));

        CreatePersonalTrainingRequest request = new CreatePersonalTrainingRequest(
            LocalDate.now().plusDays(2), LocalTime.of(18, 0), LocalTime.of(19, 0), "Trening", null);

        assertThrows(IllegalStateException.class, () -> service.createMy(regularId, request));
        verify(trainingRepository, never()).save(any());
    }

    // ========== ownership ==========

    @Test
    void shouldRejectUpdateWhenTrainingBelongsToAnotherAthlete() {
        // Given
        UUID trainingId = UUID.randomUUID();
        PersonalTraining foreign = buildTraining(buildAthlete(UUID.randomUUID()), false);
        when(trainingRepository.findById(trainingId)).thenReturn(Optional.of(foreign));

        CreatePersonalTrainingRequest request = new CreatePersonalTrainingRequest(
            LocalDate.now().plusDays(2), LocalTime.of(18, 0), LocalTime.of(19, 0), "Trening", null);

        // When / Then — same message as not-found (no id probing)
        assertThrows(IllegalArgumentException.class, () -> service.updateMy(athleteId, trainingId, request));
    }

    // ========== completion ==========

    @Test
    void shouldStoreFeedbackAndRpeWhenCompleting() {
        // Given
        UUID trainingId = UUID.randomUUID();
        PersonalTraining training = buildTraining(athlete, false);
        when(trainingRepository.findById(trainingId)).thenReturn(Optional.of(training));

        // When
        PersonalTrainingDto dto = service.complete(athleteId, trainingId,
            new CompleteTrainingRequest("Ciężko, ale poszło", 7));

        // Then
        assertTrue(training.isCompleted());
        assertEquals(7, training.getRpe());
        assertEquals("Ciężko, ale poszło", training.getFeedback());
        assertEquals("COMPLETED", dto.status());
    }

    @Test
    void shouldClearFeedbackAndRpeWhenUncompleting() {
        UUID trainingId = UUID.randomUUID();
        PersonalTraining training = buildTraining(athlete, false);
        training.complete("ok", 5);
        when(trainingRepository.findById(trainingId)).thenReturn(Optional.of(training));

        service.uncomplete(athleteId, trainingId);

        assertFalse(training.isCompleted());
        assertNull(training.getFeedback());
        assertNull(training.getRpe());
    }

    // ========== status derivation ==========

    @Test
    void shouldDeriveStatusFromCompletionAndTime() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 15, 12, 0);

        PersonalTraining planned = buildTraining(athlete, false);
        setField(planned, "trainingDate", LocalDate.of(2026, 7, 15));
        setField(planned, "startTime", LocalTime.of(13, 0));
        setField(planned, "endTime", LocalTime.of(14, 0));
        assertEquals("PLANNED", TrainingCalendarService.deriveStatus(planned, now));

        PersonalTraining missed = buildTraining(athlete, false);
        setField(missed, "trainingDate", LocalDate.of(2026, 7, 15));
        setField(missed, "startTime", LocalTime.of(10, 0));
        setField(missed, "endTime", LocalTime.of(11, 0));
        assertEquals("MISSED", TrainingCalendarService.deriveStatus(missed, now));

        missed.complete(null, null);
        assertEquals("COMPLETED", TrainingCalendarService.deriveStatus(missed, now));
    }

    // ========== notifications ==========

    @Test
    void shouldSumCoachTrainingsAndCommentsInAthleteNotifications() {
        // Given
        Instant seen = Instant.parse("2026-07-01T10:00:00Z");
        when(readRepository.findByUserIdAndAthleteId(athleteId, athleteId))
            .thenReturn(Optional.of(readWithSeenAt(seen)));
        when(trainingRepository.countCoachChangesSince(athleteId, seen)).thenReturn(2L);
        when(commentRepository.countCoachCommentsSince(athleteId, seen)).thenReturn(3L);

        // When
        TrainingNotificationsDto dto = service.getAthleteNotifications(athleteId);

        // Then
        assertEquals(5L, dto.newCount());
    }

    @Test
    void shouldCountEverythingWhenNoSeenMarkerExists() {
        when(readRepository.findByUserIdAndAthleteId(athleteId, athleteId)).thenReturn(Optional.empty());
        when(trainingRepository.countCoachChangesSince(eq(athleteId), eq(Instant.EPOCH))).thenReturn(1L);
        when(commentRepository.countCoachCommentsSince(eq(athleteId), eq(Instant.EPOCH))).thenReturn(0L);

        assertEquals(1L, service.getAthleteNotifications(athleteId).newCount());
    }

    @Test
    void shouldUpsertSeenMarkerWhenMarkingSeen() {
        // When
        service.markAthleteSeen(athleteId);

        // Then — race-free native upsert (athlete's own row: userId == athleteId)
        verify(readRepository).upsertSeen(eq(athleteId), eq(athleteId), any(Instant.class));
    }

    @Test
    void shouldRejectMarkSeenForNonAthlete() {
        UUID regularId = UUID.randomUUID();
        User regular = new User("user@example.com", "Jan", "Kowalski", "+48123456789", "jan");
        when(userRepository.findById(regularId)).thenReturn(Optional.of(regular));

        assertThrows(IllegalStateException.class, () -> service.markAthleteSeen(regularId));
        verify(readRepository, never()).upsertSeen(any(), any(), any());
    }

    // ========== range & unread flags ==========

    @Test
    void shouldFlagCoachCreatedTrainingAsUnreadInAthleteRange() {
        // Given: seen a week ago; coach added a training yesterday, athlete added one today
        Instant seen = Instant.now().minusSeconds(7 * 24 * 3600);
        when(readRepository.findByUserIdAndAthleteId(athleteId, athleteId))
            .thenReturn(Optional.of(readWithSeenAt(seen)));

        PersonalTraining byCoach = buildTraining(athlete, true);
        setField(byCoach, "createdAt", Instant.now().minusSeconds(24 * 3600));
        setField(byCoach, "updatedAt", Instant.now().minusSeconds(24 * 3600));
        setField(byCoach, "id", UUID.randomUUID());

        PersonalTraining own = buildTraining(athlete, false);
        setField(own, "createdAt", Instant.now());
        setField(own, "updatedAt", Instant.now());
        setField(own, "id", UUID.randomUUID());

        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to = LocalDate.now().plusDays(7);
        when(trainingRepository.findByAthleteIdAndTrainingDateBetweenOrderByTrainingDateAscStartTimeAsc(athleteId, from, to))
            .thenReturn(List.of(byCoach, own));
        when(commentRepository.findTrainingIdsWithNewComments(athleteId, true, seen)).thenReturn(List.of());
        when(reservationRepository.findConfirmedByUserIdInRange(athleteId, from, to)).thenReturn(List.of());

        // When
        CalendarRangeDto range = service.getMyRange(athleteId, from, to);

        // Then
        assertEquals(2, range.trainings().size());
        assertTrue(range.trainings().get(0).hasUnreadActivity(), "coach-created after seen should be unread");
        assertFalse(range.trainings().get(1).hasUnreadActivity(), "athlete's own training should not be unread");
    }

    @Test
    void shouldRejectRangeLongerThanLimit() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        assertThrows(IllegalArgumentException.class,
            () -> service.getMyRange(athleteId, from, from.plusDays(100)));
    }

    // ========== invitation overlay ==========

    @Test
    void shouldIncludeOnlyInRangePendingInvitationsInRange() {
        // Given: two pending slot invites — one inside the viewed range, one outside
        LocalDate from = LocalDate.now().plusDays(1);
        LocalDate to = from.plusDays(6);

        TimeSlot inRange = new TimeSlot(from.plusDays(2), LocalTime.of(10, 0), LocalTime.of(12, 0), 4);
        setField(inRange, "id", UUID.randomUUID());
        TimeSlot outOfRange = new TimeSlot(to.plusDays(5), LocalTime.of(10, 0), LocalTime.of(12, 0), 4);
        setField(outOfRange, "id", UUID.randomUUID());
        when(reservedSeatRepository.findUpcomingPendingSlotInvitesByUserId(eq(athleteId), any(), any()))
            .thenReturn(List.of(new ReservedSeat(inRange, athlete), new ReservedSeat(outOfRange, athlete)));
        when(readRepository.findByUserIdAndAthleteId(athleteId, athleteId)).thenReturn(Optional.empty());
        when(trainingRepository.findByAthleteIdAndTrainingDateBetweenOrderByTrainingDateAscStartTimeAsc(athleteId, from, to))
            .thenReturn(List.of());
        when(reservationRepository.findConfirmedByUserIdInRange(athleteId, from, to)).thenReturn(List.of());

        // When
        CalendarRangeDto range = service.getMyRange(athleteId, from, to);

        // Then — only the in-range invite, carrying the slot id for the booking deep-link
        assertEquals(1, range.invitations().size());
        assertEquals(inRange.getId(), range.invitations().get(0).slotId());
        assertNull(range.invitations().get(0).eventId());
    }

    // ========== reservation overlay "new" flag ==========

    @Test
    void shouldFlagFreshAthleteBookingAsNewOnlyInCoachView() {
        // Given: coach last looked a week ago; since then the athlete booked a slot,
        // an admin added a manual booking, and one booking predates the visit
        UUID adminId = UUID.randomUUID();
        Instant seen = Instant.now().minusSeconds(7 * 24 * 3600);
        when(readRepository.findByUserIdAndAthleteId(adminId, athleteId))
            .thenReturn(Optional.of(readWithSeenAt(seen)));

        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(6);
        TimeSlot slot = new TimeSlot(from.plusDays(2), LocalTime.of(10, 0), LocalTime.of(12, 0), 4);
        setField(slot, "id", UUID.randomUUID());

        Reservation byAthlete = reservationWithCreatedAt(slot, Instant.now().minusSeconds(24 * 3600));
        Reservation byAdmin = reservationWithCreatedAt(slot, Instant.now().minusSeconds(24 * 3600));
        byAdmin.setCreatedByAdmin(true);
        Reservation oldBooking = reservationWithCreatedAt(slot, seen.minusSeconds(3600));
        when(reservationRepository.findConfirmedByUserIdInRange(athleteId, from, to))
            .thenReturn(List.of(byAthlete, byAdmin, oldBooking));

        // When
        CalendarRangeDto range = service.getRangeForAthlete(adminId, athleteId, from, to);

        // Then — only the athlete's fresh booking carries the unread dot
        assertEquals(3, range.reservations().size());
        assertTrue(range.reservations().get(0).isNew(), "fresh athlete booking must be marked new");
        assertFalse(range.reservations().get(1).isNew(), "coach's own manual booking is never new");
        assertFalse(range.reservations().get(2).isNew(), "booking made before the last visit is not new");
    }

    @Test
    void shouldNeverFlagReservationAsNewForAthleteViewer() {
        // Given: athlete never opened the calendar (seen = EPOCH, everything counts as after)
        when(readRepository.findByUserIdAndAthleteId(athleteId, athleteId)).thenReturn(Optional.empty());

        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(6);
        TimeSlot slot = new TimeSlot(from.plusDays(1), LocalTime.of(10, 0), LocalTime.of(12, 0), 4);
        setField(slot, "id", UUID.randomUUID());
        when(reservationRepository.findConfirmedByUserIdInRange(athleteId, from, to))
            .thenReturn(List.of(reservationWithCreatedAt(slot, Instant.now())));

        // When
        CalendarRangeDto range = service.getMyRange(athleteId, from, to);

        // Then — the athlete made the booking themself; no dot in their own view
        assertEquals(1, range.reservations().size());
        assertFalse(range.reservations().get(0).isNew());
    }

    // ========== coach totals ==========

    @Test
    void shouldIncludeNewReservationsInTotalActivity() {
        UUID adminId = UUID.randomUUID();
        when(userRepository.findAllByAthleteTrueOrderByFirstNameAscLastNameAsc()).thenReturn(List.of(athlete));
        when(reservationRepository.countNewReservationsPerAthlete(adminId))
            .thenReturn(List.of(new AthleteActivityCount(athleteId, 2)));

        assertEquals(2L, service.getTotalAthleteActivity(adminId));
    }

    @Test
    void shouldCountOnlyFlaggedAthletesInTotalActivity() {
        // Given: two users with activity, only one still flagged
        UUID adminId = UUID.randomUUID();
        UUID unflaggedId = UUID.randomUUID();
        when(userRepository.findAllByAthleteTrueOrderByFirstNameAscLastNameAsc()).thenReturn(List.of(athlete));
        setField(athlete, "id", athleteId);
        when(trainingRepository.countNewAthleteTrainingsPerAthlete(adminId))
            .thenReturn(List.of(new AthleteActivityCount(athleteId, 2), new AthleteActivityCount(unflaggedId, 5)));
        when(trainingRepository.countNewCompletionsPerAthlete(adminId))
            .thenReturn(List.of(new AthleteActivityCount(athleteId, 1)));
        when(commentRepository.countNewAthleteCommentsPerAthlete(adminId)).thenReturn(List.of());

        // When / Then — unflagged athlete's 5 must not leak into the badge
        assertEquals(3L, service.getTotalAthleteActivity(adminId));
    }

    // ========== rpe & completion flag hygiene ==========

    @Test
    void shouldRejectCompletingTrainingThatHasNotStarted() {
        // A future plan cannot be checked off — only started (or past) sessions can
        UUID trainingId = UUID.randomUUID();
        PersonalTraining future = buildTraining(athlete, false, LocalDate.now().plusDays(2));
        when(trainingRepository.findById(trainingId)).thenReturn(Optional.of(future));

        assertThrows(IllegalStateException.class,
            () -> service.complete(athleteId, trainingId, new CompleteTrainingRequest("za wcześnie", 5)));
        assertFalse(future.isCompleted());
    }

    @Test
    void shouldRejectRpeOutsideRangeOnComplete() {
        UUID trainingId = UUID.randomUUID();
        PersonalTraining training = buildTraining(athlete, false);
        when(trainingRepository.findById(trainingId)).thenReturn(Optional.of(training));

        assertThrows(IllegalArgumentException.class,
            () -> service.complete(athleteId, trainingId, new CompleteTrainingRequest(null, 0)));
        assertThrows(IllegalArgumentException.class,
            () -> service.complete(athleteId, trainingId, new CompleteTrainingRequest(null, 11)));
        assertFalse(training.isCompleted());
    }

    @Test
    void shouldResetCoachFlagWhenAthleteCompletesOrUncompletes() {
        // Given: the coach edited the training last (flag true). The athlete's completion
        // bumps updatedAt — without the reset it would light the athlete's OWN badge.
        UUID trainingId = UUID.randomUUID();
        PersonalTraining training = buildTraining(athlete, true);
        assertTrue(training.isLastModifiedByAdmin());
        when(trainingRepository.findById(trainingId)).thenReturn(Optional.of(training));

        // When / Then
        service.complete(athleteId, trainingId, new CompleteTrainingRequest("ok", 5));
        assertFalse(training.isLastModifiedByAdmin());

        service.uncomplete(athleteId, trainingId);
        assertFalse(training.isLastModifiedByAdmin());
    }

    @Test
    void shouldTrackWhoEditedLast() {
        // Athlete edit clears the coach flag; coach edit sets it
        UUID trainingId = UUID.randomUUID();
        PersonalTraining training = buildTraining(athlete, true);
        when(trainingRepository.findById(trainingId)).thenReturn(Optional.of(training));
        CreatePersonalTrainingRequest request = new CreatePersonalTrainingRequest(
            LocalDate.now().plusDays(2), LocalTime.of(18, 0), LocalTime.of(19, 0), "Trening", null);

        service.updateMy(athleteId, trainingId, request);
        assertFalse(training.isLastModifiedByAdmin());

        service.updateAsAdmin(trainingId, request);
        assertTrue(training.isLastModifiedByAdmin());
        assertTrue(training.isCreatedByAdmin(), "provenance must survive edits");
    }

    @Test
    void shouldSetCreatedByAdminWhenCoachCreatesForAthlete() {
        when(trainingRepository.save(any())).thenAnswer(inv -> {
            PersonalTraining t = inv.getArgument(0);
            setField(t, "createdAt", Instant.now());
            return t;
        });
        CreatePersonalTrainingRequest request = new CreatePersonalTrainingRequest(
            LocalDate.now().plusDays(2), LocalTime.of(18, 0), LocalTime.of(19, 0), "Obwód", null);

        PersonalTrainingDto dto = service.createForAthlete(athleteId, request);

        assertTrue(dto.createdByAdmin());
    }

    @Test
    void shouldRejectCoachCreateForUnflaggedUser() {
        UUID regularId = UUID.randomUUID();
        User regular = new User("user@example.com", "Jan", "Kowalski", "+48123456789", "jan");
        when(userRepository.findById(regularId)).thenReturn(Optional.of(regular));

        CreatePersonalTrainingRequest request = new CreatePersonalTrainingRequest(
            LocalDate.now().plusDays(2), LocalTime.of(18, 0), LocalTime.of(19, 0), "Obwód", null);

        assertThrows(IllegalArgumentException.class, () -> service.createForAthlete(regularId, request));
        verify(trainingRepository, never()).save(any());
    }

    // ========== deletion alerts ==========

    @Test
    void shouldRecordDeletionOnlyForFutureTraining() {
        // Future training deleted by the athlete -> logged for the coach
        UUID futureId = UUID.randomUUID();
        PersonalTraining future = buildTraining(athlete, false, LocalDate.now().plusDays(2));
        when(trainingRepository.findById(futureId)).thenReturn(Optional.of(future));

        service.deleteMy(athleteId, futureId);

        ArgumentCaptor<TrainingDeletion> captor = ArgumentCaptor.forClass(TrainingDeletion.class);
        verify(deletionRepository).save(captor.capture());
        assertFalse(captor.getValue().isDeletedByAdmin());
        assertEquals("Trening", captor.getValue().getTitle());

        // Past training deleted -> just journal tidying, no alert
        UUID pastId = UUID.randomUUID();
        PersonalTraining past = buildTraining(athlete, false, LocalDate.now().minusDays(2));
        when(trainingRepository.findById(pastId)).thenReturn(Optional.of(past));

        service.deleteMy(athleteId, pastId);

        verify(deletionRepository, times(1)).save(any());
    }

    @Test
    void shouldRecordAdminDeletionOfFutureTrainingForAthlete() {
        UUID trainingId = UUID.randomUUID();
        PersonalTraining future = buildTraining(athlete, true, LocalDate.now().plusDays(2));
        when(trainingRepository.findById(trainingId)).thenReturn(Optional.of(future));

        service.deleteAsAdmin(trainingId);

        ArgumentCaptor<TrainingDeletion> captor = ArgumentCaptor.forClass(TrainingDeletion.class);
        verify(deletionRepository).save(captor.capture());
        assertTrue(captor.getValue().isDeletedByAdmin());
    }

    @Test
    void shouldIncludeAdminDeletionsInAthleteNotifications() {
        Instant seen = Instant.parse("2026-07-01T10:00:00Z");
        when(readRepository.findByUserIdAndAthleteId(athleteId, athleteId))
            .thenReturn(Optional.of(readWithSeenAt(seen)));
        when(trainingRepository.countCoachChangesSince(athleteId, seen)).thenReturn(0L);
        when(commentRepository.countCoachCommentsSince(athleteId, seen)).thenReturn(0L);
        when(deletionRepository.countAdminDeletionsSince(athleteId, seen)).thenReturn(2L);

        assertEquals(2L, service.getAthleteNotifications(athleteId).newCount());
    }

    // ========== comments ==========

    @Test
    void shouldEscapeHtmlWhenAddingComment() {
        UUID trainingId = UUID.randomUUID();
        PersonalTraining training = buildTraining(athlete, false);
        when(trainingRepository.findById(trainingId)).thenReturn(Optional.of(training));
        when(commentRepository.save(any())).thenAnswer(inv -> {
            TrainingComment c = inv.getArgument(0);
            setField(c, "createdAt", Instant.now());
            return c;
        });

        TrainingCommentDto dto = service.addMyComment(athleteId, trainingId, "<script>alert(1)</script> zwisy");

        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt; zwisy", dto.body());
        assertFalse(dto.authorIsAdmin());
        assertTrue(dto.mine());
    }

    @Test
    void shouldRejectBlankComment() {
        UUID trainingId = UUID.randomUUID();
        PersonalTraining training = buildTraining(athlete, false);
        when(trainingRepository.findById(trainingId)).thenReturn(Optional.of(training));

        assertThrows(IllegalArgumentException.class, () -> service.addMyComment(athleteId, trainingId, "   "));
        verify(commentRepository, never()).save(any());
    }

    @Test
    void shouldRejectCommentAccessToForeignTraining() {
        UUID trainingId = UUID.randomUUID();
        PersonalTraining foreign = buildTraining(buildAthlete(UUID.randomUUID()), false);
        when(trainingRepository.findById(trainingId)).thenReturn(Optional.of(foreign));

        assertThrows(IllegalArgumentException.class, () -> service.getMyComments(athleteId, trainingId));
        assertThrows(IllegalArgumentException.class, () -> service.addMyComment(athleteId, trainingId, "hej"));
        assertThrows(IllegalArgumentException.class, () -> service.deleteMy(athleteId, trainingId));
    }

    // ========== sanitization ==========

    @Test
    void shouldClampSanitizedTextToMaxLength() {
        String longTitle = "x".repeat(300);
        String sanitized = PersonalTraining.sanitizeText(longTitle, PersonalTraining.MAX_TITLE_LENGTH);
        assertNotNull(sanitized);
        assertEquals(PersonalTraining.MAX_TITLE_LENGTH, sanitized.length());
        // Polish diacritics must survive the UTF-8 escape variant
        assertEquals("zwisy na chwytach ąęółż", PersonalTraining.sanitizeText("zwisy na chwytach ąęółż", 100));
    }

    // ========== attachments (links) ==========

    @Test
    void shouldPersistAttachmentsInOrderWhenCreating() {
        // Given
        when(trainingRepository.save(any())).thenAnswer(inv -> {
            PersonalTraining t = inv.getArgument(0);
            setField(t, "createdAt", Instant.now());
            return t;
        });
        CreatePersonalTrainingRequest request = new CreatePersonalTrainingRequest(
            LocalDate.now().plusDays(2), LocalTime.of(18, 0), LocalTime.of(19, 30), "T", null,
            List.of(new AttachmentRequest("https://youtu.be/dQw4w9WgXcQ", "Wideo"),
                    new AttachmentRequest("https://docs.google.com/plan", "Plan")));

        // When
        service.createMy(athleteId, request);

        // Then
        ArgumentCaptor<TrainingAttachment> cap = ArgumentCaptor.forClass(TrainingAttachment.class);
        verify(attachmentRepository, times(2)).save(cap.capture());
        List<TrainingAttachment> saved = cap.getAllValues();
        assertEquals(0, saved.get(0).getPosition());
        assertEquals(1, saved.get(1).getPosition());
        assertEquals("Wideo", saved.get(0).getLabel());
    }

    @Test
    void shouldRejectWhenMoreThanThreeAttachments() {
        // Given
        CreatePersonalTrainingRequest request = new CreatePersonalTrainingRequest(
            LocalDate.now().plusDays(2), LocalTime.of(18, 0), LocalTime.of(19, 30), "T", null,
            List.of(link("https://a.com"), link("https://b.com"), link("https://c.com"), link("https://d.com")));

        // When / Then
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> service.createMy(athleteId, request));
        assertEquals("training.attachment.too.many", e.getMessage());
        verify(trainingRepository, never()).save(any());
    }

    @Test
    void shouldRejectAttachmentWithNonHttpUrl() {
        // Given: javascript: scheme (host null) must be rejected
        CreatePersonalTrainingRequest request = new CreatePersonalTrainingRequest(
            LocalDate.now().plusDays(2), LocalTime.of(18, 0), LocalTime.of(19, 30), "T", null,
            List.of(new AttachmentRequest("javascript:alert(1)", null)));

        // When / Then
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> service.createMy(athleteId, request));
        assertEquals("training.attachment.url.invalid", e.getMessage());
    }

    @Test
    void shouldSanitizeAttachmentLabelWhenPersisting() {
        // Given
        when(trainingRepository.save(any())).thenAnswer(inv -> {
            PersonalTraining t = inv.getArgument(0);
            setField(t, "createdAt", Instant.now());
            return t;
        });
        CreatePersonalTrainingRequest request = new CreatePersonalTrainingRequest(
            LocalDate.now().plusDays(2), LocalTime.of(18, 0), LocalTime.of(19, 30), "T", null,
            List.of(new AttachmentRequest("https://a.com", "<b>Plan</b>")));

        // When
        service.createMy(athleteId, request);

        // Then
        ArgumentCaptor<TrainingAttachment> cap = ArgumentCaptor.forClass(TrainingAttachment.class);
        verify(attachmentRepository).save(cap.capture());
        assertEquals("&lt;b&gt;Plan&lt;/b&gt;", cap.getValue().getLabel());
    }

    @Test
    void shouldReplaceAttachmentsWhenUpdatePassesList() {
        // Given
        UUID trainingId = UUID.randomUUID();
        PersonalTraining training = buildTraining(athlete, false);
        setField(training, "id", trainingId);
        when(trainingRepository.findById(trainingId)).thenReturn(Optional.of(training));
        CreatePersonalTrainingRequest request = new CreatePersonalTrainingRequest(
            LocalDate.now().plusDays(2), LocalTime.of(18, 0), LocalTime.of(19, 30), "T", null,
            List.of(link("https://a.com")));

        // When
        service.updateMy(athleteId, trainingId, request);

        // Then: replace-all = wipe then reinsert
        verify(attachmentRepository).deleteByTrainingId(trainingId);
        verify(attachmentRepository).save(any());
    }

    @Test
    void shouldKeepAttachmentsWhenUpdateOmitsThem() {
        // Given: 5-arg request → attachments null → move/drag must NOT touch links
        UUID trainingId = UUID.randomUUID();
        PersonalTraining training = buildTraining(athlete, false);
        setField(training, "id", trainingId);
        when(trainingRepository.findById(trainingId)).thenReturn(Optional.of(training));
        CreatePersonalTrainingRequest request = new CreatePersonalTrainingRequest(
            LocalDate.now().plusDays(2), LocalTime.of(18, 0), LocalTime.of(19, 30), "T", null);

        // When
        service.updateMy(athleteId, trainingId, request);

        // Then
        verify(attachmentRepository, never()).deleteByTrainingId(any());
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void shouldClearAttachmentsWhenUpdatePassesEmptyList() {
        // Given
        UUID trainingId = UUID.randomUUID();
        PersonalTraining training = buildTraining(athlete, false);
        setField(training, "id", trainingId);
        when(trainingRepository.findById(trainingId)).thenReturn(Optional.of(training));
        CreatePersonalTrainingRequest request = new CreatePersonalTrainingRequest(
            LocalDate.now().plusDays(2), LocalTime.of(18, 0), LocalTime.of(19, 30), "T", null,
            List.of());

        // When
        service.updateMy(athleteId, trainingId, request);

        // Then: wipe, but nothing reinserted
        verify(attachmentRepository).deleteByTrainingId(trainingId);
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void shouldExposeEmbedUrlForYoutubeAttachment() {
        // Given
        TrainingAttachment a = new TrainingAttachment(
            buildTraining(athlete, false), "https://youtu.be/dQw4w9WgXcQ", "V", 0);

        // When
        TrainingAttachmentDto dto = TrainingCalendarService.toAttachmentDto(a);

        // Then
        assertEquals("https://www.youtube.com/embed/dQw4w9WgXcQ", dto.embedUrl());
    }

    @Test
    void shouldExposeNullEmbedUrlForPlainLink() {
        TrainingAttachment a = new TrainingAttachment(
            buildTraining(athlete, false), "https://docs.google.com/plan", "Plan", 0);
        assertNull(TrainingCalendarService.toAttachmentDto(a).embedUrl());
    }

    private static AttachmentRequest link(String url) {
        return new AttachmentRequest(url, null);
    }

    // ========== helpers ==========

    private static User buildAthlete(UUID id) {
        User user = new User("athlete@example.com", "Anna", "Wspinaczka", "+48123456789", "anna");
        user.setAthlete(true);
        setField(user, "id", id);
        return user;
    }

    // Yesterday by default: completion requires a training that has already started
    private static PersonalTraining buildTraining(User athlete, boolean byAdmin) {
        return buildTraining(athlete, byAdmin, LocalDate.now().minusDays(1));
    }

    private static PersonalTraining buildTraining(User athlete, boolean byAdmin, LocalDate date) {
        PersonalTraining training = new PersonalTraining(
            athlete, date, LocalTime.of(18, 0), LocalTime.of(19, 30),
            "Trening", null, byAdmin);
        setField(training, "createdAt", Instant.now());
        setField(training, "updatedAt", Instant.now());
        return training;
    }

    private Reservation reservationWithCreatedAt(TimeSlot slot, Instant createdAt) {
        Reservation reservation = new Reservation(athlete, slot);
        setField(reservation, "createdAt", createdAt);
        return reservation;
    }

    private static TrainingCalendarRead readWithSeenAt(Instant seenAt) {
        TrainingCalendarRead read = new TrainingCalendarRead(UUID.randomUUID(), UUID.randomUUID());
        setField(read, "seenAt", seenAt);
        return read;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
