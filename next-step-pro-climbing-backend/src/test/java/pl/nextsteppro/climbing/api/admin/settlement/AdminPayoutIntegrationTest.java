package pl.nextsteppro.climbing.api.admin.settlement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventType;
import pl.nextsteppro.climbing.domain.reservation.GuestReservationRepository;
import pl.nextsteppro.climbing.domain.reservation.Reservation;
import pl.nextsteppro.climbing.domain.timeslot.TimeSlot;
import pl.nextsteppro.climbing.domain.user.User;
import pl.nextsteppro.climbing.domain.user.UserRole;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Work somebody else settles in bulk: extra classes at a school, sessions run for a club.
 *
 * <p>The test this file exists for is {@code shouldDeriveWhatThePlaceActuallyPaysPerSession} — the
 * rest is ordinary coverage, but that one is the number the whole feature is kept for.
 */
class AdminPayoutIntegrationTest extends BaseIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);
    private static final LocalDate OCTOBER = LocalDate.of(2026, 10, 1);

    @Autowired private AdminPayoutService payoutService;
    @Autowired private AdminSettlementService settlementService;
    @Autowired private AdminSettlementStatsService stats;
    @Autowired private GuestReservationRepository guestReservationRepository;
    @Autowired private JdbcTemplate jdbc;

    private UUID school;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM settlements");
        jdbc.update("DELETE FROM payouts");
        jdbc.update("DELETE FROM session_payouts");
        jdbc.update("DELETE FROM payout_sources");
        guestReservationRepository.deleteAll();
        reservationRepository.deleteAll();
        timeSlotRepository.deleteAll();
        eventRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();

        school = payoutService.createSource(new SavePayoutSourceRequest("SP nr 12")).id();
    }

    // ------------------------------------------------------------------ sources

    @Test
    @DisplayName("shouldRefuseASecondActivePayerWithTheSameName")
    void shouldRefuseASecondActivePayerWithTheSameName() {
        assertThrows(IllegalArgumentException.class,
            () -> payoutService.createSource(new SavePayoutSourceRequest("  sp NR 12 ")),
            "Case and padding are not a different school");
    }

    @Test
    @DisplayName("shouldLetAnArchivedNameBeUsedAgain")
    void shouldLetAnArchivedNameBeUsedAgain() {
        payoutService.setSourceArchived(school, true);

        UUID reopened = payoutService.createSource(new SavePayoutSourceRequest("SP nr 12")).id();

        // A collaboration that comes back next season must not collide with its own dead row.
        assertNotNull(reopened);
        assertEquals(2, payoutService.listSources().size());
        assertTrue(payoutService.listSources().stream().anyMatch(PayoutSourceDto::archived),
            "Archived, not deleted: the transfers it paid still point at it");
    }

    // -------------------------------------------------------------- the point

    @Test
    @DisplayName("shouldDeriveWhatThePlaceActuallyPaysPerSession")
    void shouldDeriveWhatThePlaceActuallyPaysPerSession() {
        for (int i = 0; i < 12; i++) {
            assignSlot(OCTOBER.plusDays(i), school);
        }
        // One transfer, arriving the month after the work, for the whole of it.
        payoutService.createPayout(new SavePayoutRequest(
            school, OCTOBER.plusDays(9), new BigDecimal("1400"), LocalDate.of(2026, 11, 8)));

        PayoutPeriodDto october = periodOf(LocalDate.of(2026, 11, 30), OCTOBER);

        assertEquals(12, october.sessions());
        assertEquals(0, new BigDecimal("1400.00").compareTo(october.amount()));
        // 1400 / 12 -- the figure that is nowhere else in the app, and the reason to keep this at all.
        assertEquals(0, new BigDecimal("116.67").compareTo(october.ratePerSession()));
    }

    @Test
    @DisplayName("shouldShowAMonthOfWorkThatHasNotBeenPaidForYet")
    void shouldShowAMonthOfWorkThatHasNotBeenPaidForYet() {
        assignSlot(OCTOBER.plusDays(1), school);
        assignSlot(OCTOBER.plusDays(8), school);

        PayoutPeriodDto october = periodOf(LocalDate.of(2026, 11, 30), OCTOBER);

        // The most useful row on the table: the invoice nobody has paid. Listing only what arrived
        // would hide precisely this.
        assertEquals(2, october.sessions());
        assertEquals(0, BigDecimal.ZERO.compareTo(october.amount()));
        assertNull(october.ratePerSession(),
            "A rate needs both halves; a zero here would be a claim rather than a gap");
    }

    @Test
    @DisplayName("shouldShowATransferAgainstNoMarkedSessions")
    void shouldShowATransferAgainstNoMarkedSessions() {
        payoutService.createPayout(new SavePayoutRequest(
            school, OCTOBER, new BigDecimal("800"), LocalDate.of(2026, 11, 8)));

        PayoutPeriodDto october = periodOf(LocalDate.of(2026, 11, 30), OCTOBER);

        // The mirror case: money arrived but the calendar was never marked, so there is no rate.
        assertEquals(0, october.sessions());
        assertEquals(0, new BigDecimal("800.00").compareTo(october.amount()));
        assertNull(october.ratePerSession());
    }

    @Test
    @DisplayName("shouldSumSeveralTransfersForOneMonthRatherThanRefuseTheSecond")
    void shouldSumSeveralTransfersForOneMonthRatherThanRefuseTheSecond() {
        assignSlot(OCTOBER.plusDays(1), school);
        payoutService.createPayout(new SavePayoutRequest(
            school, OCTOBER, new BigDecimal("500"), LocalDate.of(2026, 11, 8)));
        // A correction or a second tranche for the same month happens; refusing it would force the
        // figures to be merged by hand and lose that they arrived separately.
        payoutService.createPayout(new SavePayoutRequest(
            school, OCTOBER, new BigDecimal("120"), LocalDate.of(2026, 11, 20)));

        assertEquals(0, new BigDecimal("620.00")
            .compareTo(periodOf(LocalDate.of(2026, 11, 30), OCTOBER).amount()));
    }

    @Test
    @DisplayName("shouldSnapAnyDayOfTheMonthToItsFirst")
    void shouldSnapAnyDayOfTheMonthToItsFirst() {
        payoutService.createPayout(new SavePayoutRequest(
            school, LocalDate.of(2026, 10, 27), new BigDecimal("300"), LocalDate.of(2026, 11, 8)));

        // Read back through the real query rather than raw JDBC: inside a transactional test the
        // INSERT has not been flushed yet, and JPQL is what forces it.
        assertEquals(OCTOBER, periodOf(LocalDate.of(2026, 11, 30), OCTOBER).month(),
            "The period is a month, so every day of it lands on the same period");
    }

    // ------------------------------------------------------- revenue and queue

    @Test
    @DisplayName("shouldCountATransferAsRevenueOnTheDayItArrived")
    void shouldCountATransferAsRevenueOnTheDayItArrived() {
        payoutService.createPayout(new SavePayoutRequest(
            school, OCTOBER, new BigDecimal("1400"), LocalDate.of(2026, 11, 8)));

        RevenueDto revenue = stats.buildOverview("2026", LocalDate.of(2026, 11, 30)).revenue();

        // Same axis as a settled amount, so a month keeps one total no matter which way money came in.
        assertEquals(0, new BigDecimal("1400.00").compareTo(revenue.total()));
        assertEquals(0, new BigDecimal("1400.00").compareTo(revenue.fromPayouts()));
        assertEquals(0, BigDecimal.ZERO.compareTo(revenue.fromSlots()));
    }

    @Test
    @DisplayName("shouldKeepASessionSettledInBulkOutOfThePricingQueue")
    void shouldKeepASessionSettledInBulkOutOfThePricingQueue() {
        User pupil = saveUser("pupil@example.com");
        TimeSlot slot = timeSlotRepository.saveAndFlush(
            new TimeSlot(TODAY.minusDays(3), LocalTime.of(16, 0), LocalTime.of(17, 0), 10));
        reservationRepository.saveAndFlush(new Reservation(pupil, slot));

        assertEquals(1, stats.buildOverview("2026", TODAY).unpriced().count(),
            "Before it is marked it is ordinary unpriced work");

        payoutService.assignSource("slot", slot.getId(), new AssignPayoutSourceRequest(school));

        // Nobody to charge per head, so asking to price it would be asking for a made-up number.
        assertEquals(0, stats.buildOverview("2026", TODAY).unpriced().count());

        payoutService.assignSource("slot", slot.getId(), new AssignPayoutSourceRequest(null));
        assertEquals(1, stats.buildOverview("2026", TODAY).unpriced().count(),
            "Unmarking puts it back — the queue follows the mark, not a one-way flag");
    }

    @Test
    @DisplayName("shouldTellTheSectionWhichPayerASessionBelongsTo")
    void shouldTellTheSectionWhichPayerASessionBelongsTo() {
        TimeSlot slot = timeSlotRepository.saveAndFlush(
            new TimeSlot(TODAY.plusDays(3), LocalTime.of(16, 0), LocalTime.of(17, 0), 10));

        assertNull(settlementService.getSection("slot", slot.getId()).payoutSourceId());

        payoutService.assignSource("slot", slot.getId(), new AssignPayoutSourceRequest(school));

        SettlementSectionDto section = settlementService.getSection("slot", slot.getId());
        assertEquals(school, section.payoutSourceId());
        assertEquals("SP nr 12", section.payoutSourceName(),
            "The name travels with the id so the modal can say who settles this without a second call");
    }

    @Test
    @DisplayName("shouldRefuseToMarkASingleDayOfAnEvent")
    void shouldRefuseToMarkASingleDayOfAnEvent() {
        Event event = eventRepository.saveAndFlush(
            new Event("Kurs", EventType.COURSE, TODAY, TODAY.plusDays(1), 8));
        TimeSlot eventDay = timeSlotRepository.saveAndFlush(
            new TimeSlot(event, TODAY, LocalTime.of(9, 0), LocalTime.of(17, 0), 8));

        // Same addressable-session rule as the per-participant half, from the same guard.
        assertThrows(IllegalArgumentException.class, () -> payoutService.assignSource(
            "slot", eventDay.getId(), new AssignPayoutSourceRequest(school)));
    }

    @Test
    @DisplayName("shouldMoveASessionBetweenPayersWithoutLeavingTwoMarks")
    void shouldMoveASessionBetweenPayersWithoutLeavingTwoMarks() {
        UUID club = payoutService.createSource(new SavePayoutSourceRequest("Klub XYZ")).id();
        TimeSlot slot = timeSlotRepository.saveAndFlush(
            new TimeSlot(TODAY.plusDays(3), LocalTime.of(16, 0), LocalTime.of(17, 0), 10));

        payoutService.assignSource("slot", slot.getId(), new AssignPayoutSourceRequest(school));
        payoutService.assignSource("slot", slot.getId(), new AssignPayoutSourceRequest(club));

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM session_payouts", Integer.class),
            "One session belongs to one payer — reassigning overwrites rather than adding");
        assertEquals(club, settlementService.getSection("slot", slot.getId()).payoutSourceId());
    }

    @Test
    @DisplayName("shouldRefuseToMarkASessionThatAlreadyHasPerParticipantAmounts")
    void shouldRefuseToMarkASessionThatAlreadyHasPerParticipantAmounts() {
        User pupil = saveUser("pupil2@example.com");
        TimeSlot slot = timeSlotRepository.saveAndFlush(
            new TimeSlot(TODAY.plusDays(3), LocalTime.of(16, 0), LocalTime.of(17, 0), 10));
        reservationRepository.saveAndFlush(new Reservation(pupil, slot));
        settlementService.save("slot", slot.getId(), "user", pupil.getId(),
            new SaveSettlementRequest(new BigDecimal("150"), null));

        // ⚠️ Otherwise the amount stays in the table, the section stops showing it, and it goes on
        // counting in revenue and in outstanding debt — money visible in the totals and nowhere else.
        assertThrows(IllegalArgumentException.class, () -> payoutService.assignSource(
            "slot", slot.getId(), new AssignPayoutSourceRequest(school)));

        settlementService.delete("slot", slot.getId(), "user", pupil.getId());
        payoutService.assignSource("slot", slot.getId(), new AssignPayoutSourceRequest(school));
        assertEquals(school, settlementService.getSection("slot", slot.getId()).payoutSourceId(),
            "Clearing the amounts is what unblocks it — the guard rejects the change, not the state");
    }

    @Test
    @DisplayName("shouldRefuseAPerParticipantAmountOnASessionSettledInBulk")
    void shouldRefuseAPerParticipantAmountOnASessionSettledInBulk() {
        User pupil = saveUser("pupil3@example.com");
        TimeSlot slot = timeSlotRepository.saveAndFlush(
            new TimeSlot(TODAY.plusDays(3), LocalTime.of(16, 0), LocalTime.of(17, 0), 10));
        reservationRepository.saveAndFlush(new Reservation(pupil, slot));
        payoutService.assignSource("slot", slot.getId(), new AssignPayoutSourceRequest(school));

        // The mirror direction. The front hides the fields, but the endpoint must not depend on that.
        assertThrows(IllegalArgumentException.class, () -> settlementService.save(
            "slot", slot.getId(), "user", pupil.getId(),
            new SaveSettlementRequest(new BigDecimal("150"), null)));
    }

    @Test
    @DisplayName("shouldListTheIndividualTransfersSoAMistypedOneCanBeRemoved")
    void shouldListTheIndividualTransfersSoAMistypedOneCanBeRemoved() {
        assignSlot(OCTOBER.plusDays(1), school);
        payoutService.createPayout(new SavePayoutRequest(
            school, OCTOBER, new BigDecimal("1400"), LocalDate.of(2026, 11, 8)));
        UUID typo = payoutService.createPayout(new SavePayoutRequest(
            school, OCTOBER, new BigDecimal("14000"), LocalDate.of(2026, 11, 9)));

        PayoutPeriodDto before = periodOf(LocalDate.of(2026, 11, 30), OCTOBER);
        assertEquals(2, before.transfers().size(), "Write-only would make a fat finger permanent");

        payoutService.deletePayout(typo);

        assertEquals(0, new BigDecimal("1400.00")
            .compareTo(periodOf(LocalDate.of(2026, 11, 30), OCTOBER).amount()));
    }

    @Test
    @DisplayName("shouldCountEveryTransferInTheEverythingViewNotJustTheChartedMonths")
    void shouldCountEveryTransferInTheEverythingViewNotJustTheChartedMonths() {
        // Older than the twelve months the chart rolls over.
        payoutService.createPayout(new SavePayoutRequest(
            school, LocalDate.of(2024, 3, 1), new BigDecimal("900"), LocalDate.of(2024, 4, 5)));

        RevenueDto revenue = stats.buildOverview("all", LocalDate.of(2026, 11, 30)).revenue();

        // Settlements are read all-time in this view, so clipping transfers to the chart made the
        // all-time total quietly short.
        assertEquals(0, new BigDecimal("900.00").compareTo(revenue.fromPayouts()));
    }

    @Test
    @DisplayName("shouldRejectAnUnknownPayerAndAnAmountOutOfRange")
    void shouldRejectAnUnknownPayerAndAnAmountOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> payoutService.createPayout(
            new SavePayoutRequest(UUID.randomUUID(), OCTOBER, BigDecimal.TEN, TODAY)));
        assertThrows(IllegalArgumentException.class, () -> payoutService.createPayout(
            new SavePayoutRequest(school, OCTOBER, new BigDecimal("-1"), TODAY)));
    }

    // ------------------------------------------------------------------ fixtures

    private User saveUser(String email) {
        User user = new User(email, "Uczen", "Szkolny", "+48123456789", email.split("@")[0]);
        user.setRole(UserRole.USER);
        user.setEmailVerified(true);
        return userRepository.saveAndFlush(user);
    }

    private void assignSlot(LocalDate date, UUID sourceId) {
        TimeSlot slot = timeSlotRepository.saveAndFlush(
            new TimeSlot(date, LocalTime.of(16, 0), LocalTime.of(17, 0), 10));
        payoutService.assignSource("slot", slot.getId(), new AssignPayoutSourceRequest(sourceId));
    }

    private PayoutPeriodDto periodOf(LocalDate today, LocalDate month) {
        List<PayoutPeriodDto> periods = stats.buildOverview("2026", today).payouts().periods();
        return periods.stream()
            .filter(period -> period.month().equals(month))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no row for " + month + " in " + periods));
    }
}
