package pl.nextsteppro.climbing.api.admin.settlement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.nextsteppro.climbing.domain.event.Event;
import pl.nextsteppro.climbing.domain.event.EventType;
import pl.nextsteppro.climbing.domain.reservation.GuestReservation;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Settlements tab's arithmetic, with the clock passed in so the month buckets are testable.
 *
 * <p>The two rules worth breaking a build over are that revenue is counted on the payment date while
 * debt is counted on the session date, and that outstanding debt ignores the year filter entirely.
 */
class AdminSettlementStatsTest extends BaseIntegrationTest {

    /** A fixed "today" so the twelve rolling buckets do not move under the assertions. */
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 31);

    @Autowired private AdminSettlementStatsService stats;
    @Autowired private GuestReservationRepository guestReservationRepository;
    @Autowired private JdbcTemplate jdbc;

    private User client;
    private User other;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM settlements");
        guestReservationRepository.deleteAll();
        reservationRepository.deleteAll();
        timeSlotRepository.deleteAll();
        eventRepository.deleteAll();
        authTokenRepository.deleteAll();
        userRepository.deleteAll();

        client = saveUser("client@example.com", "Anna", "Kowalska");
        other = saveUser("other@example.com", "Piotr", "Nowak");
    }

    @Test
    @DisplayName("shouldCountRevenueOnThePaymentDateAndDebtOnTheSessionDate")
    void shouldCountRevenueOnThePaymentDateAndDebtOnTheSessionDate() {
        // Held in December, paid in January: revenue of January, session of December.
        settleSlot(LocalDate.of(2025, 12, 20), client, "300", LocalDate.of(2026, 1, 8));
        // Held in March, never paid: debt dated March.
        settleSlot(LocalDate.of(2026, 3, 12), other, "450", null);

        SettlementOverviewDto overview = stats.buildOverview("2026", TODAY);

        assertEquals(0, new BigDecimal("300.00").compareTo(overview.revenue().total()),
            "The December session's money arrived in 2026, so 2026 is where it counts");
        assertEquals(0, new BigDecimal("300.00").compareTo(monthOf(overview, LocalDate.of(2026, 1, 1))));
        assertEquals(0, BigDecimal.ZERO.compareTo(monthOf(overview, LocalDate.of(2025, 12, 1))),
            "2025-12 is not one of 2026's buckets, and the year's chart must not invent it");

        assertEquals(0, new BigDecimal("450.00").compareTo(overview.outstanding().total()));
        assertEquals(LocalDate.of(2026, 3, 12), overview.outstanding().oldest());
    }

    @Test
    @DisplayName("shouldKeepOutstandingDebtVisibleRegardlessOfTheSelectedYear")
    void shouldKeepOutstandingDebtVisibleRegardlessOfTheSelectedYear() {
        settleSlot(LocalDate.of(2024, 5, 4), other, "200", null);
        settleSlot(LocalDate.of(2026, 6, 1), client, "150", LocalDate.of(2026, 6, 1));

        for (String year : new String[]{"2024", "2026", "all"}) {
            SettlementOverviewDto overview = stats.buildOverview(year, TODAY);
            assertEquals(0, new BigDecimal("200.00").compareTo(overview.outstanding().total()),
                "A debt from two years ago is still a debt — the year picker must not hide it "
                    + "(year=" + year + ")");
            assertEquals(1, overview.outstanding().count());
        }
    }

    @Test
    @DisplayName("shouldSplitRevenueBetweenOneToOneSlotsAndEvents")
    void shouldSplitRevenueBetweenOneToOneSlotsAndEvents() {
        settleSlot(LocalDate.of(2026, 4, 2), client, "150", LocalDate.of(2026, 4, 2));
        settleEvent(LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 12), other, "600",
            LocalDate.of(2026, 5, 10));

        SettlementOverviewDto overview = stats.buildOverview("2026", TODAY);

        assertEquals(0, new BigDecimal("150.00").compareTo(overview.revenue().fromSlots()));
        assertEquals(0, new BigDecimal("600.00").compareTo(overview.revenue().fromEvents()));
        assertEquals(0, new BigDecimal("750.00").compareTo(overview.revenue().total()));
    }

    @Test
    @DisplayName("shouldAverageOverTheMonthsTheDataSpansRatherThanOverTwelve")
    void shouldAverageOverTheMonthsTheDataSpansRatherThanOverTwelve() {
        settleSlot(LocalDate.of(2026, 9, 1), client, "1000", LocalDate.of(2026, 9, 1));
        settleSlot(LocalDate.of(2026, 10, 1), other, "2000", LocalDate.of(2026, 10, 1));

        SettlementOverviewDto overview = stats.buildOverview("2026", TODAY);

        assertEquals(0, new BigDecimal("1500.00").compareTo(overview.revenue().monthlyAverage()),
            "Two months of trading averaged over twelve reads as a sixth of what was earned");
    }

    @Test
    @DisplayName("shouldHideTheAverageWhenNothingHasBeenPaid")
    void shouldHideTheAverageWhenNothingHasBeenPaid() {
        settleSlot(LocalDate.of(2026, 3, 12), other, "450", null);

        SettlementOverviewDto overview = stats.buildOverview("2026", TODAY);

        assertNull(overview.revenue().monthlyAverage(),
            "Null so the tile disappears — a zero average is a claim, not an absence");
        assertEquals(12, overview.revenue().months().size(),
            "Always twelve buckets: a chart that changes height with its data is hard to read");
    }

    @Test
    @DisplayName("shouldRankPayersByWhatTheyPaidAndShowWhatTheyStillOwe")
    void shouldRankPayersByWhatTheyPaidAndShowWhatTheyStillOwe() {
        settleSlot(LocalDate.of(2026, 2, 1), client, "150", LocalDate.of(2026, 2, 1));
        settleSlot(LocalDate.of(2026, 3, 1), client, "150", LocalDate.of(2026, 3, 1));
        settleSlot(LocalDate.of(2026, 4, 1), other, "100", LocalDate.of(2026, 4, 1));
        settleSlot(LocalDate.of(2026, 5, 1), other, "450", null);

        SettlementOverviewDto overview = stats.buildOverview("2026", TODAY);

        assertEquals(2, overview.people().size());
        PersonRevenueDto top = overview.people().getFirst();
        assertEquals("Anna Kowalska", top.name());
        assertEquals(client.getId(), top.userId(), "A registered payer links to their user card");
        assertEquals(2, top.paymentCount());
        assertEquals(0, new BigDecimal("300.00").compareTo(top.paid()));
        assertEquals(LocalDate.of(2026, 3, 1), top.lastPayment());

        PersonRevenueDto second = overview.people().get(1);
        assertEquals(0, new BigDecimal("100.00").compareTo(second.paid()));
        assertEquals(0, new BigDecimal("450.00").compareTo(second.outstanding()));
    }

    @Test
    @DisplayName("shouldNameAGuestWithoutOfferingALinkToAUserCard")
    void shouldNameAGuestWithoutOfferingALinkToAUserCard() {
        Event event = eventRepository.saveAndFlush(
            new Event("Wyjazd", EventType.WORKSHOP, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3), 8));
        UUID guestId = guestReservationRepository.saveAndFlush(
            new GuestReservation(event, "Ekipa z Krakowa", 3)).getId();
        jdbc.update("INSERT INTO settlements (event_id, guest_reservation_id, amount) VALUES (?, ?, 1800)",
            event.getId(), guestId);
        jdbc.update("INSERT INTO payments (guest_reservation_id, amount, received_on, entered_event_id) "
            + "VALUES (?, 1800, ?, ?)", guestId, LocalDate.of(2026, 7, 1), event.getId());

        PersonRevenueDto guest = stats.buildOverview("2026", TODAY).people().getFirst();

        assertEquals("guest", guest.payerType());
        assertEquals("Ekipa z Krakowa", guest.name());
        assertNull(guest.userId(), "No account, so no card to link to — the null IS the signal");
    }

    @Test
    @DisplayName("shouldDefaultToTheNewestYearHoldingDataRatherThanTheCurrentOne")
    void shouldDefaultToTheNewestYearHoldingDataRatherThanTheCurrentOne() {
        settleSlot(LocalDate.of(2025, 6, 1), client, "150", LocalDate.of(2025, 6, 1));

        SettlementOverviewDto overview = stats.buildOverview(null, LocalDate.of(2027, 1, 4));

        assertEquals(2025, overview.year(),
            "An empty January of a new year looks exactly like lost history");
        assertEquals(0, new BigDecimal("150.00").compareTo(overview.revenue().total()));
        assertTrue(overview.years().contains(2025));
    }

    @Test
    @DisplayName("shouldFallBackToTheCurrentYearWhenThereIsNoDataAtAll")
    void shouldFallBackToTheCurrentYearWhenThereIsNoDataAtAll() {
        SettlementOverviewDto overview = stats.buildOverview(null, TODAY);

        assertEquals(2026, overview.year());
        assertEquals(0, overview.years().size());
        assertEquals(0, BigDecimal.ZERO.compareTo(overview.revenue().total()));
        assertEquals(0, overview.outstanding().count());
    }

    @Test
    @DisplayName("shouldRollTheChartBackTwelveMonthsWhenNoYearIsSelected")
    void shouldRollTheChartBackTwelveMonthsWhenNoYearIsSelected() {
        settleSlot(LocalDate.of(2026, 8, 1), client, "150", LocalDate.of(2026, 8, 1));

        SettlementOverviewDto overview = stats.buildOverview("all", TODAY);

        assertNull(overview.year());
        assertEquals(LocalDate.of(2025, 9, 1), overview.revenue().months().getFirst().month());
        assertEquals(LocalDate.of(2026, 8, 1), overview.revenue().months().getLast().month());
    }

    @Test
    @DisplayName("shouldRejectAnUnparseableYear")
    void shouldRejectAnUnparseableYear() {
        assertThrows(IllegalArgumentException.class, () -> stats.buildOverview("wczoraj", TODAY));
        assertThrows(IllegalArgumentException.class, () -> stats.buildOverview("12", TODAY));
    }

    @Test
    @DisplayName("shouldCompareEachMonthWithTheSameMonthAYearEarlier")
    void shouldCompareEachMonthWithTheSameMonthAYearEarlier() {
        settleSlot(LocalDate.of(2025, 9, 4), client, "800", LocalDate.of(2025, 9, 4));
        settleSlot(LocalDate.of(2026, 9, 4), client, "1000", LocalDate.of(2026, 9, 4));

        RevenueDto revenue = stats.buildOverview("2026", TODAY).revenue();

        // Climbing is seasonal, so month against previous month calls a quiet October a bad month
        // when it is simply October. Only the same month a year earlier answers "is this going up".
        assertEquals(0, new BigDecimal("1000.00").compareTo(revenue.total()));
        assertEquals(0, new BigDecimal("800.00").compareTo(revenue.previousTotal()));
        assertEquals(12, revenue.previousMonths().size());
        assertEquals(LocalDate.of(2025, 1, 1), revenue.previousMonths().getFirst().month());
        assertEquals(0, new BigDecimal("800.00").compareTo(
            revenue.previousMonths().stream()
                .filter(m -> m.month().equals(LocalDate.of(2025, 9, 1)))
                .findFirst().orElseThrow().amount()));
    }

    @Test
    @DisplayName("shouldOfferNoComparisonForTheEverythingView")
    void shouldOfferNoComparisonForTheEverythingView() {
        settleSlot(LocalDate.of(2026, 9, 4), client, "1000", LocalDate.of(2026, 9, 4));

        RevenueDto revenue = stats.buildOverview("all", TODAY).revenue();

        // "Everything" has no previous; shifting its rolling window would compare two arbitrary spans.
        assertTrue(revenue.previousMonths().isEmpty());
        assertEquals(0, BigDecimal.ZERO.compareTo(revenue.previousTotal()));
    }

    @Test
    @DisplayName("shouldKeepLastYearsMoneyOutOfThisYearsFigures")
    void shouldKeepLastYearsMoneyOutOfThisYearsFigures() {
        settleSlot(LocalDate.of(2025, 5, 1), client, "500", LocalDate.of(2025, 5, 1));
        settleSlot(LocalDate.of(2026, 5, 1), other, "300", LocalDate.of(2026, 5, 1));

        SettlementOverviewDto overview = stats.buildOverview("2026", TODAY);

        // The read now spans two years so the comparison is free — but every other figure still
        // filters on the selected one, and a leak here would inflate the year silently.
        assertEquals(0, new BigDecimal("300.00").compareTo(overview.revenue().total()));
        assertEquals(1, overview.people().size(), "Last year's payer must not appear in this year");
    }

    @Test
    @DisplayName("shouldExportWhatWasOwedAndWhatWasHandedOverAsTwoLists")
    void shouldExportWhatWasOwedAndWhatWasHandedOverAsTwoLists() {
        settleSlot(LocalDate.of(2026, 3, 4), client, "150", LocalDate.of(2026, 3, 4));
        settleSlot(LocalDate.of(2026, 5, 1), other, "450", null);

        SettlementExportDto export = stats.exportRows("2026", "Klient", "Wyplata");
        List<SettlementExportRowDto> lines = export.lines();

        assertEquals(2, lines.size());
        assertEquals(LocalDate.of(2026, 3, 4), lines.getFirst().paidOn());
        assertEquals("Anna Kowalska", lines.getFirst().payer());
        assertEquals(0, new BigDecimal("150.00").compareTo(lines.getFirst().covered()));
        // ⚠️ The unpaid line is present with an empty payment date — dropping it would make the file
        // impossible to reconcile against the screen it came from.
        assertNull(lines.get(1).paidOn());
        assertEquals(0, new BigDecimal("450.00").compareTo(lines.get(1).amount()));

        assertEquals(1, export.payments().size(), "One payment arrived, and it is listed as it was handed over");
        assertEquals(0, new BigDecimal("150.00").compareTo(export.payments().getFirst().amount()));
        assertEquals("Anna Kowalska", export.payments().getFirst().payer());
    }

    // ------------------------------------------------- sessions nobody priced yet

    @Test
    @DisplayName("shouldListAPastSessionThatNobodyPricedAndDropItOncePriced")
    void shouldListAPastSessionThatNobodyPricedAndDropItOncePriced() {
        TimeSlot slot = pastSlot(TODAY.minusDays(3));
        reservationRepository.saveAndFlush(new Reservation(client, slot));

        UnpricedDto unpriced = stats.buildOverview("2026", TODAY).unpriced();
        assertEquals(1, unpriced.count(),
            "A session nobody priced is neither revenue nor debt, so this list is the only place "
                + "it can appear at all");
        UnpricedSessionDto session = unpriced.sessions().getFirst();
        assertEquals("slot", session.targetType());
        assertEquals(slot.getId(), session.targetId());
        assertEquals(1, session.payerCount());

        jdbc.update("INSERT INTO settlements (time_slot_id, user_id, amount) VALUES (?, ?, 150)",
            slot.getId(), client.getId());

        assertEquals(0, stats.buildOverview("2026", TODAY).unpriced().count(),
            "Pricing it is what takes it off the queue — nothing else should");
    }

    @Test
    @DisplayName("shouldNotAskToPriceASessionThatHasNotHappenedYet")
    void shouldNotAskToPriceASessionThatHasNotHappenedYet() {
        TimeSlot upcoming = timeSlotRepository.saveAndFlush(
            new TimeSlot(TODAY.plusDays(5), LocalTime.of(18, 0), LocalTime.of(20, 0), 4));
        reservationRepository.saveAndFlush(new Reservation(client, upcoming));

        assertEquals(0, stats.buildOverview("2026", TODAY).unpriced().count(),
            "This is a queue of work already done; every future booking in it would be noise");
    }

    @Test
    @DisplayName("shouldCountEveryUnpricedAttendeeOfAMultiDayEventAsOneSession")
    void shouldCountEveryUnpricedAttendeeOfAMultiDayEventAsOneSession() {
        LocalDate start = TODAY.minusDays(10);
        Event event = eventRepository.saveAndFlush(
            new Event("Kurs skalny", EventType.COURSE, start, start.plusDays(2), 8));
        for (int day = 0; day <= 2; day++) {
            TimeSlot eventDay = timeSlotRepository.saveAndFlush(
                new TimeSlot(event, start.plusDays(day), LocalTime.of(9, 0), LocalTime.of(17, 0), 8));
            reservationRepository.saveAndFlush(new Reservation(client, eventDay));
            reservationRepository.saveAndFlush(new Reservation(other, eventDay));
        }

        UnpricedDto unpriced = stats.buildOverview("2026", TODAY).unpriced();

        assertEquals(1, unpriced.count(), "Three days and two people are still one thing to price");
        // ⚠️ The count is people, not booking rows: six reservations, two of them distinct payers.
        assertEquals(2, unpriced.sessions().getFirst().payerCount(),
            "Collapsing the per-day rows must not also collapse two different people");
        assertEquals("event", unpriced.sessions().getFirst().targetType());
    }

    @Test
    @DisplayName("shouldCountAGuestWrittenOntoOneDayOfAnEventTowardsThatEvent")
    void shouldCountAGuestWrittenOntoOneDayOfAnEventTowardsThatEvent() {
        LocalDate start = TODAY.minusDays(10);
        Event event = eventRepository.saveAndFlush(
            new Event("Wyjazd", EventType.WORKSHOP, start, start.plusDays(1), 8));
        TimeSlot eventDay = timeSlotRepository.saveAndFlush(
            new TimeSlot(event, start, LocalTime.of(9, 0), LocalTime.of(17, 0), 8));
        guestReservationRepository.saveAndFlush(
            new GuestReservation(eventDay, "Marek dopisany z widoku dnia", 1));

        UnpricedDto unpriced = stats.buildOverview("2026", TODAY).unpriced();

        assertEquals(1, unpriced.count());
        assertEquals(event.getId(), unpriced.sessions().getFirst().targetId(),
            "A guest hangs on a day slot, but the amount is written on the event — so the queue has "
                + "to point at the address that can actually be priced");
    }

    @Test
    @DisplayName("shouldIgnoreACancelledBookingAndAnythingOlderThanTheWindow")
    void shouldIgnoreACancelledBookingAndAnythingOlderThanTheWindow() {
        TimeSlot cancelled = pastSlot(TODAY.minusDays(2));
        Reservation gone = reservationRepository.saveAndFlush(new Reservation(client, cancelled));
        gone.cancel();
        reservationRepository.saveAndFlush(gone);

        TimeSlot ancient = pastSlot(TODAY.minusDays(AdminSettlementStatsService.UNPRICED_WINDOW_DAYS + 5));
        reservationRepository.saveAndFlush(new Reservation(other, ancient));

        UnpricedDto unpriced = stats.buildOverview("2026", TODAY).unpriced();

        assertEquals(0, unpriced.count(),
            "Nobody attended the cancelled one, and the old one is archive rather than a chore");
        assertEquals(AdminSettlementStatsService.UNPRICED_WINDOW_DAYS, unpriced.windowDays(),
            "The screen states the window it applies, so it has to be told what it is");
    }

    @Test
    @DisplayName("shouldKeepTheQueueUnchangedByTheYearPicker")
    void shouldKeepTheQueueUnchangedByTheYearPicker() {
        TimeSlot slot = pastSlot(TODAY.minusDays(3));
        reservationRepository.saveAndFlush(new Reservation(client, slot));

        for (String year : new String[]{"2024", "2026", "all"}) {
            assertEquals(1, stats.buildOverview(year, TODAY).unpriced().count(),
                "Work you never priced does not stop being unpriced because you looked at another "
                    + "year (year=" + year + ")");
        }
    }

    // ------------------------------------------------------- sessions with no payer

    /**
     * The gap this list exists for: a session with nobody on it cannot reach the unpriced queue,
     * because that queue is built from reservations and guests. Its cost is quiet — the hourly rate
     * divides one transfer by fewer sessions and reads high, with nothing saying the denominator is
     * short.
     */
    @Test
    @DisplayName("shouldReportAWorkedSessionThatHasNobodyToBillAtAll")
    void shouldReportAWorkedSessionThatHasNobodyToBillAtAll() {
        TimeSlot worked = contractorSlot(TODAY.minusDays(4));

        SettlementOverviewDto overview = stats.buildOverview("2026", TODAY);

        assertEquals(1, overview.unassigned().count(),
            "Zero participants means the unpriced queue can never see it, so this list is the only "
                + "place it can appear at all");
        UnassignedSessionDto session = overview.unassigned().sessions().getFirst();
        assertEquals("slot", session.targetType());
        assertEquals(worked.getId(), session.targetId());
        assertEquals(AdminSettlementStatsService.UNPRICED_WINDOW_DAYS, overview.unassigned().windowDays(),
            "The screen states the window it applies, so it has to be told what it is");

        assignToSource(worked, "SP nr 5");

        assertEquals(0, stats.buildOverview("2026", TODAY).unassigned().count(),
            "Naming the payer is what takes it off the queue — nothing else should");
    }

    /**
     * ⚠️ The exclusions are the whole feature. Zero seats is what separates work from an unsold
     * hour, and an absence has zero seats by construction — so without these the list is a wall of
     * holiday and empty offers, which is the same as not having the list.
     */
    @Test
    @DisplayName("shouldNotReportTimeThatWasNeverWorkedOrHoursNobodyTookUp")
    void shouldNotReportTimeThatWasNeverWorkedOrHoursNobodyTookUp() {
        TimeSlot absence = timeSlotRepository.saveAndFlush(
            new TimeSlot(TODAY.minusDays(3), LocalTime.of(9, 0), LocalTime.of(17, 0), 4));
        absence.setUnavailable(true);
        timeSlotRepository.saveAndFlush(absence);

        TimeSlot cancelled = contractorSlot(TODAY.minusDays(3));
        cancelled.block("odwołane");
        timeSlotRepository.saveAndFlush(cancelled);

        TimeSlot window = timeSlotRepository.saveAndFlush(
            new TimeSlot(TODAY.minusDays(3), LocalTime.of(12, 0), LocalTime.of(13, 0), 0));
        window.setAvailabilityWindow(true);
        timeSlotRepository.saveAndFlush(window);

        // An hour that was on offer and nobody took: not work, and by far the most common empty slot.
        pastSlot(TODAY.minusDays(3));
        // Older than the window.
        contractorSlot(TODAY.minusDays(AdminSettlementStatsService.UNPRICED_WINDOW_DAYS + 1L));
        // The same exclusions hold ahead of today, now that the list reaches there.
        TimeSlot futureAbsence = timeSlotRepository.saveAndFlush(
            new TimeSlot(TODAY.plusDays(3), LocalTime.of(9, 0), LocalTime.of(17, 0), 4));
        futureAbsence.setUnavailable(true);
        timeSlotRepository.saveAndFlush(futureAbsence);

        assertEquals(0, stats.buildOverview("2026", TODAY).unassigned().count(),
            "An absence, a cancelled session, an availability window, an unsold hour and an "
                + "archived one are each a different reason this list must stay quiet");
        assertEquals(0, stats.countUnassigned(TODAY), "and the nav dot with it");
    }

    /**
     * The list is where the admin-nav dot leads, and the dot is there so a session planned without
     * a payer is caught while it is still cheap to fix — so the list must hold upcoming sessions too,
     * and the two counts must be the same number.
     */
    @Test
    @DisplayName("shouldListUpcomingSessionsWithNoPayerAndCountThemForTheNavDot")
    void shouldListUpcomingSessionsWithNoPayerAndCountThemForTheNavDot() {
        TimeSlot done = contractorSlot(TODAY.minusDays(2));
        TimeSlot today = contractorSlot(TODAY);
        TimeSlot planned = contractorSlot(TODAY.plusDays(40));

        SettlementOverviewDto overview = stats.buildOverview("2026", TODAY);

        assertEquals(List.of(done.getId(), today.getId(), planned.getId()),
            overview.unassigned().sessions().stream().map(UnassignedSessionDto::targetId).toList());
        assertEquals(List.of(false, false, true),
            overview.unassigned().sessions().stream().map(UnassignedSessionDto::upcoming).toList(),
            "Today sits with the past, at day resolution like the window; only later days are 'upcoming'");
        assertEquals(overview.unassigned().count(), stats.countUnassigned(TODAY),
            "A dot and the list it opens that disagree by one look like a bug nobody can resolve");

        assignToSource(planned, "SP nr 5");

        assertEquals(2, stats.countUnassigned(TODAY), "Naming the payer is what puts the dot out");
    }

    /**
     * ⚠️ The two queues must never report the same session: two counts of one backlog can only
     * disagree, and the admin has no way to tell which one is lying.
     */
    @Test
    @DisplayName("shouldHandASessionToExactlyOneOfTheTwoQueues")
    void shouldHandASessionToExactlyOneOfTheTwoQueues() {
        TimeSlot withGuest = contractorSlot(TODAY.minusDays(5));
        guestReservationRepository.saveAndFlush(new GuestReservation(withGuest, "Ekipa ze szkoły", 8));

        TimeSlot priced = contractorSlot(TODAY.minusDays(6));
        jdbc.update("INSERT INTO settlements (time_slot_id, user_id, amount) VALUES (?, ?, 150)",
            priced.getId(), client.getId());

        SettlementOverviewDto overview = stats.buildOverview("2026", TODAY);

        assertEquals(0, overview.unassigned().count(),
            "A session with somebody on it belongs to the pricing queue, and one already priced "
                + "belongs to neither — reporting either here would count one backlog twice");
        assertEquals(1, overview.unpriced().count(),
            "And the guest's session must still be asking to be priced");
    }

    /**
     * Oldest first, like the debts and the pricing queue: the useful order for a backlog is the
     * order it accumulated in. The ordering lives in the query, so nothing in Java would notice it
     * being dropped — the list would simply come back in whatever order Postgres felt like.
     */
    @Test
    @DisplayName("shouldListSessionsWithNoPayerOldestFirst")
    void shouldListSessionsWithNoPayerOldestFirst() {
        contractorSlot(TODAY.minusDays(2));
        contractorSlot(TODAY.minusDays(30));
        contractorSlot(TODAY.minusDays(16));

        List<LocalDate> dates = stats.buildOverview("2026", TODAY).unassigned().sessions().stream()
            .map(UnassignedSessionDto::date)
            .toList();

        assertEquals(List.of(TODAY.minusDays(30), TODAY.minusDays(16), TODAY.minusDays(2)), dates);
    }

    @Test
    @DisplayName("shouldKeepTheNoPayerListUnchangedByTheYearPicker")
    void shouldKeepTheNoPayerListUnchangedByTheYearPicker() {
        contractorSlot(TODAY.minusDays(7));

        for (String year : new String[]{"2024", "2026", "all"}) {
            assertEquals(1, stats.buildOverview(year, TODAY).unassigned().count(),
                "Work with no payer does not acquire one because you looked at another year "
                    + "(year=" + year + ")");
        }
    }

    /**
     * The same question the backlog list answers, asked about a visible calendar range — including
     * sessions still ahead, which is the point: the reminder is worth more while the week is being
     * planned than a month later, when the only fix left is remembering what happened.
     */
    @Test
    @DisplayName("shouldMarkClosedSessionsWithNoPayerAcrossTheVisibleRange")
    void shouldMarkClosedSessionsWithNoPayerAcrossTheVisibleRange() {
        TimeSlot upcoming = contractorSlot(TODAY.plusDays(2));
        TimeSlot done = contractorSlot(TODAY.minusDays(2));
        TimeSlot assigned = contractorSlot(TODAY.plusDays(3));
        assignToSource(assigned, "SP nr 5");
        // An ordinary hour on offer that nobody took is not this list's business, here either.
        pastSlot(TODAY.minusDays(1));

        UnassignedMarkersDto markers = stats.getUnassignedMarkers(TODAY.minusDays(7), TODAY.plusDays(7));

        assertEquals(Set.of(upcoming.getId(), done.getId()), Set.copyOf(markers.slotIds()));
        assertEquals(Set.of(TODAY.plusDays(2), TODAY.minusDays(2)), Set.copyOf(markers.slotDates()));
    }

    /** Ids and days, never a name: the payer is what a calendar payload must not learn. */
    @Test
    @DisplayName("shouldRefuseAMarkerRangeWiderThanOneScreenCanShow")
    void shouldRefuseAMarkerRangeWiderThanOneScreenCanShow() {
        assertThrows(IllegalArgumentException.class,
            () -> stats.getUnassignedMarkers(TODAY, TODAY.plusDays(400)));
        assertThrows(IllegalArgumentException.class,
            () -> stats.getUnassignedMarkers(TODAY, TODAY.minusDays(1)));
    }

    /**
     * Deleting a session takes what it CHARGED with it, but not the money somebody handed over.
     *
     * <p>The cascade lives in the schema (V92, V93), so nothing in Java would notice a migration
     * that changed those foreign keys to SET NULL: the charges would survive as orphans pointing at
     * nothing, and the debt list would keep chasing a session that is gone while the rate kept
     * dividing by it. ⚠️ The payment is the opposite on purpose (V100, {@code ON DELETE SET NULL}):
     * it was handed over by a person, the session was only where it was typed in, so it stays as
     * their credit instead of vanishing from revenue. Deleting through the repository rather than
     * the admin service on purpose — this pins the schema's promise, which every delete path relies on.
     */
    @Test
    @DisplayName("shouldTakeTheChargeWithADeletedSessionButKeepTheMoneyHandedOver")
    void shouldTakeTheChargeWithADeletedSessionButKeepTheMoneyHandedOver() {
        settleSlot(LocalDate.of(2026, 5, 4), client, "150", LocalDate.of(2026, 5, 4));
        settleSlot(LocalDate.of(2026, 5, 6), other, "90", null);
        TimeSlot forSchool = contractorSlot(LocalDate.of(2026, 5, 11));
        assignToSource(forSchool, "SP nr 5");

        SettlementOverviewDto before = stats.buildOverview("2026", TODAY);
        assertEquals(0, new BigDecimal("150.00").compareTo(before.revenue().fromSlots()));
        assertEquals(1, before.payouts().periods().size(), "the school's May is on the table");
        assertEquals(1, before.outstanding().count());

        timeSlotRepository.deleteAll();

        SettlementOverviewDto after = stats.buildOverview("2026", TODAY);
        assertTrue(after.payouts().periods().isEmpty(),
            "The rate went on dividing by a session that was deleted");
        assertEquals(0, after.outstanding().count(), "Nobody is chased for a session that is gone");
        assertEquals(0, after.unassigned().count());
        assertEquals(0, new BigDecimal("150.00").compareTo(after.revenue().total()),
            "The 150 still arrived");
        assertEquals(0, BigDecimal.ZERO.compareTo(after.revenue().fromSlots()),
            "but no session earned it any more");
        assertEquals(0, new BigDecimal("150.00").compareTo(after.credits().total()),
            "so it is her credit, waiting for the next session");
    }

    /**
     * Which months are paid has to be readable without reading the amount column as a status.
     *
     * <p>⚠️ The month still being worked is IN_PROGRESS, not overdue — it used to wear the same amber
     * "no payout" as a month the payer forgot, which made the warning noise for most of every month.
     * The boundary is the month's last day in Warsaw ({@code TODAY} is 31 August).
     */
    @Test
    @DisplayName("shouldTellAPaidMonthFromARunningOneFromAnOverdueOne")
    void shouldTellAPaidMonthFromARunningOneFromAnOverdueOne() {
        UUID sourceId = createSource("Chwyciarnia");
        assignToSource(contractorSlot(LocalDate.of(2026, 6, 9)), sourceId);
        assignToSource(contractorSlot(LocalDate.of(2026, 7, 14)), sourceId);
        assignToSource(contractorSlot(LocalDate.of(2026, 8, 11)), sourceId);
        payout(sourceId, LocalDate.of(2026, 6, 1), "414", LocalDate.of(2026, 7, 7));

        Map<LocalDate, PayoutPeriodStatus> onTheTab = stats.buildOverview("2026", TODAY).payouts().periods()
            .stream().collect(Collectors.toMap(PayoutPeriodDto::month, PayoutPeriodDto::status));

        assertEquals(PayoutPeriodStatus.SETTLED, onTheTab.get(LocalDate.of(2026, 6, 1)));
        assertEquals(PayoutPeriodStatus.AWAITING, onTheTab.get(LocalDate.of(2026, 7, 1)),
            "July is over and nothing came: the invoice nobody has paid");
        assertEquals(PayoutPeriodStatus.IN_PROGRESS, onTheTab.get(LocalDate.of(2026, 8, 1)),
            "On its last day August is still being worked, not overdue");

        assertEquals(PayoutPeriodStatus.AWAITING,
            stats.sourceHistory(sourceId, TODAY.plusDays(1)).periods().getFirst().status(),
            "From the first of September August is overdue — on the payer's own screen too");

        // A second tranche only adds to the figure; a transfer of any size settles the month.
        payout(sourceId, LocalDate.of(2026, 7, 1), "0", LocalDate.of(2026, 8, 20));
        assertEquals(PayoutPeriodStatus.SETTLED,
            stats.sourceHistory(sourceId, TODAY).periods().get(1).status());
    }

    // -------------------------------------------------------- one payer's history

    /**
     * ⚠️ The chart is bucketed by the month the work was FOR, not by the day the money landed —
     * unlike revenue everywhere else here. It sits directly above rows that are period months, and
     * two axes on one screen disagree with each other in front of the reader.
     */
    @Test
    @DisplayName("shouldTellOnePayersHistoryOnTheSameAxisAsTheRowsBelowIt")
    void shouldTellOnePayersHistoryOnTheSameAxisAsTheRowsBelowIt() {
        UUID sourceId = createSource("Chwyciarnia");
        assignToSource(contractorSlot(LocalDate.of(2026, 3, 10)), sourceId);   // 90 min
        assignToSource(contractorSlot(LocalDate.of(2026, 3, 17)), sourceId);   // 90 min
        assignToSource(contractorSlot(LocalDate.of(2026, 5, 12)), sourceId);   // 90 min
        // Work of March, paid in April: the chart bar belongs to MARCH.
        payout(sourceId, LocalDate.of(2026, 3, 1), "300", LocalDate.of(2026, 4, 6));

        PayoutSourceHistoryDto history = stats.sourceHistory(sourceId);

        assertEquals("Chwyciarnia", history.name());
        assertEquals(3, history.totalSessions());
        assertEquals(270, history.totalMinutes());
        assertEquals(0, new BigDecimal("300.00").compareTo(history.totalAmount()));
        // 300 zł over 4.5 h of work.
        assertEquals(0, new BigDecimal("66.67").compareTo(history.averageRatePerHour()));

        assertEquals(LocalDate.of(2026, 3, 1), history.firstActivity());
        assertEquals(LocalDate.of(2026, 5, 1), history.lastActivity());
        assertEquals(3, history.months(), "March to May inclusive — the span, not the busy months");

        // ⚠️ April is in the chart at zero: a month with nothing in it is a fact about the
        // collaboration, and closing the gap would draw a busier partner than the data has.
        assertEquals(3, history.chart().size());
        assertEquals(LocalDate.of(2026, 3, 1), history.chart().getFirst().month());
        assertEquals(0, new BigDecimal("300.00").compareTo(history.chart().getFirst().amount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(history.chart().get(1).amount()));

        // Newest first, like every list on this tab.
        assertEquals(LocalDate.of(2026, 5, 1), history.periods().getFirst().month());
        assertEquals(2, history.periods().getLast().sessions());
        assertEquals(2, history.periods().getLast().heldSessions().size(),
            "the month rows carry their own sessions, same as on the tab");
    }

    @Test
    @DisplayName("shouldBreakOnePayersHistoryIntoYears")
    void shouldBreakOnePayersHistoryIntoYears() {
        UUID sourceId = createSource("Chwyciarnia");
        assignToSource(contractorSlot(LocalDate.of(2025, 11, 4)), sourceId);
        assignToSource(contractorSlot(LocalDate.of(2026, 2, 3)), sourceId);
        payout(sourceId, LocalDate.of(2025, 11, 1), "150", LocalDate.of(2025, 12, 1));

        List<PayoutYearDto> years = stats.sourceHistory(sourceId).years();

        assertEquals(2, years.size());
        assertEquals(2026, years.getFirst().year(), "Newest year first");
        assertNull(years.getFirst().ratePerHour(),
            "A year of work with no transfer has no rate — a zero would be a claim");
        assertEquals(2025, years.get(1).year());
        assertEquals(0, new BigDecimal("100.00").compareTo(years.get(1).ratePerHour()),
            "150 zł over an hour and a half");
    }

    /** A payer who has just been created is a real state, and the screen has to survive it. */
    @Test
    @DisplayName("shouldSurviveAPayerWithNoHistoryAtAll")
    void shouldSurviveAPayerWithNoHistoryAtAll() {
        PayoutSourceHistoryDto history = stats.sourceHistory(createSource("Nowy klub"));

        assertEquals(0, history.totalSessions());
        assertEquals(0, history.months());
        assertNull(history.firstActivity());
        assertNull(history.averageRatePerHour());
        assertTrue(history.chart().isEmpty());
        assertTrue(history.years().isEmpty());
        assertTrue(history.periods().isEmpty());
    }

    // ------------------------------------------------------------------ fixtures

    /** How the owner records work done for somebody else: a normal slot nobody can book. */
    private TimeSlot contractorSlot(LocalDate on) {
        return timeSlotRepository.saveAndFlush(
            new TimeSlot(on, LocalTime.of(16, 0), LocalTime.of(17, 30), 0));
    }

    private void assignToSource(TimeSlot slot, String name) {
        assignToSource(slot, createSource(name));
    }

    private void assignToSource(TimeSlot slot, UUID sourceId) {
        jdbc.update("INSERT INTO session_payouts (time_slot_id, payout_source_id) VALUES (?, ?)",
            slot.getId(), sourceId);
    }

    private UUID createSource(String name) {
        UUID sourceId = UUID.randomUUID();
        jdbc.update("INSERT INTO payout_sources (id, name) VALUES (?, ?)", sourceId, name);
        return sourceId;
    }

    private void payout(UUID sourceId, LocalDate periodMonth, String amount, LocalDate receivedOn) {
        jdbc.update("INSERT INTO payouts (payout_source_id, period_month, amount, received_on) "
            + "VALUES (?, ?, ?, ?)", sourceId, periodMonth, new BigDecimal(amount), receivedOn);
    }

    private TimeSlot pastSlot(LocalDate on) {
        return timeSlotRepository.saveAndFlush(
            new TimeSlot(on, LocalTime.of(18, 0), LocalTime.of(20, 0), 4));
    }

    private User saveUser(String email, String firstName, String lastName) {
        User user = new User(email, firstName, lastName, "+48123456789", email.split("@")[0]);
        user.setRole(UserRole.USER);
        user.setEmailVerified(true);
        return userRepository.saveAndFlush(user);
    }

    @Test
    @DisplayName("shouldNotCountAStandingMonthlyFeeAsSessionIncome")
    void shouldNotCountAStandingMonthlyFeeAsSessionIncome() {
        // ⚠️ A retainer is charged for the month, not for time in the calendar — the sessions it
        // covers are deliberately left unpriced. Folding it into "1:1 slots" therefore states
        // session income for somebody whose sessions all earned nothing, and the two facts sit two
        // cards apart on the same screen.
        monthlyFee(LocalDate.of(2026, 3, 1), client, "500", LocalDate.of(2026, 3, 1));

        SettlementOverviewDto overview = stats.buildOverview("2026", TODAY);

        assertEquals(0, new BigDecimal("500.00").compareTo(overview.revenue().total()),
            "It is revenue — just not revenue from a session");
        assertEquals(0, BigDecimal.ZERO.compareTo(overview.revenue().fromSlots()),
            "No slot earned anything this year");
        assertEquals(0, new BigDecimal("500.00").compareTo(overview.revenue().fromSubscriptions()));
    }

    @Test
    @DisplayName("shouldSplitEveryZlotyOfRevenueIntoExactlyOneBucket")
    void shouldSplitEveryZlotyOfRevenueIntoExactlyOneBucket() {
        // The split is drawn as one bar against the headline total, so a source missing from it is a
        // gap nobody can account for, and a source counted twice is a bar wider than its own track.
        settleSlot(LocalDate.of(2026, 2, 10), client, "150", LocalDate.of(2026, 2, 10));
        settleEvent(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 3), other, "600",
            LocalDate.of(2026, 4, 1));
        monthlyFee(LocalDate.of(2026, 3, 1), client, "500", LocalDate.of(2026, 3, 1));

        RevenueDto revenue = stats.buildOverview("2026", TODAY).revenue();

        BigDecimal parts = revenue.fromSlots()
            .add(revenue.fromEvents())
            .add(revenue.fromSubscriptions())
            .add(revenue.fromPayouts())
            .add(revenue.fromCredit());
        assertEquals(0, revenue.total().compareTo(parts),
            "The parts of the split must add up to the total they are drawn against");
    }

    // ------------------------------------------------- the client's own card

    @Test
    @DisplayName("shouldReportWhatArrivedOnTheCardNotWhatWasCharged")
    void shouldReportWhatArrivedOnTheCardNotWhatWasCharged() {
        // ⚠️ Cash rarely settles a bill exactly, which is the whole reason paid_amount exists. A card
        // that reads settled_on as "paid in full" tells the owner this client is square while the
        // Settlements tab is still chasing them — two screens, one client, two answers.
        partiallyPaidSlot(LocalDate.of(2026, 3, 12), client, "150", "100", LocalDate.of(2026, 3, 12));

        PayerSummaryDto summary = stats.payerSummary(client.getId(), 10);

        assertEquals(0, new BigDecimal("100.00").compareTo(summary.paid()),
            "A hundred arrived, so a hundred is what this client has paid");
        assertEquals(0, new BigDecimal("50.00").compareTo(summary.outstanding()),
            "And the fifty they are still short has to be on their own card, not only on the tab");
    }

    @Test
    @DisplayName("shouldCountAnOverpaymentOnTheCardAsMoneyThatArrived")
    void shouldCountAnOverpaymentOnTheCardAsMoneyThatArrived() {
        partiallyPaidSlot(LocalDate.of(2026, 3, 12), client, "150", "200", LocalDate.of(2026, 3, 12));

        PayerSummaryDto summary = stats.payerSummary(client.getId(), 10);

        assertEquals(0, new BigDecimal("200.00").compareTo(summary.paid()),
            "The change from a two-hundred note is money in hand, not something to round away");
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.outstanding()),
            "Nobody who has overpaid owes anything");
    }

    @Test
    @DisplayName("shouldMarkAStandingFeeSoItIsNotLabelledAsASession")
    void shouldMarkAStandingFeeSoItIsNotLabelledAsASession() {
        // ⚠️ A fee has no calendar entry, so it carries no title — and the client's fallback for a
        // missing title is "Trening 1:1". Without this flag the card listed a training that never
        // happened, once for every month billed.
        monthlyFee(LocalDate.of(2026, 3, 1), client, "400", null);
        partiallyPaidSlot(LocalDate.of(2026, 3, 12), client, "150", "150", LocalDate.of(2026, 3, 12));

        List<PayerLineDto> lines = stats.payerSummary(client.getId(), 10).recent();

        PayerLineDto fee = lines.stream().filter(PayerLineDto::monthlyFee).findFirst().orElseThrow();
        assertNull(fee.title(), "A fee has no session to name");
        assertEquals(1, lines.stream().filter(line -> !line.monthlyFee()).count(),
            "And the real session is not flagged as one");
    }

    @Test
    @DisplayName("shouldReportOnTheCardWhatTheClientHasLeftWithUs")
    void shouldReportOnTheCardWhatTheClientHasLeftWithUs() {
        // Fifty of the two hundred was change against a hundred-and-fifty session, and it is still
        // ours to spend on his behalf. Beside the debt, never subtracted from it: this card and the
        // tab both keep debts gross, and one screen quietly netting them is how they start
        // disagreeing about the same client.
        partiallyPaidSlot(LocalDate.of(2026, 3, 12), client, "150", "200", LocalDate.of(2026, 3, 12));

        PayerSummaryDto summary = stats.payerSummary(client.getId(), 10);

        assertEquals(0, new BigDecimal("50.00").compareTo(summary.credit()),
            "We are holding fifty of his");
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.outstanding()),
            "And he owes nothing, which is a different question");
    }

    @Test
    @DisplayName("shouldCoverALaterSessionWithTheChangeFromAnEarlierOneOnBothScreens")
    void shouldCoverALaterSessionWithTheChangeFromAnEarlierOneOnBothScreens() {
        // The case the old model needed a "credit note beside the debt" for: a hundred handed over
        // for a fifty session, then a second fifty. Since V100 the change simply covers it — nobody
        // is chased for money already handed over, and the card and the tab say the same thing.
        partiallyPaidSlot(LocalDate.of(2026, 3, 12), client, "50", "100", LocalDate.of(2026, 3, 12));
        partiallyPaidSlot(LocalDate.of(2026, 5, 7), client, "50", "0", null);

        PayerSummaryDto card = stats.payerSummary(client.getId(), 10);
        SettlementOverviewDto tab = stats.buildOverview("2026", TODAY);

        assertEquals(0, tab.outstanding().count(), "Nothing is owed");
        assertEquals(0, tab.credits().payers(), "And nothing is held");
        assertEquals(0, BigDecimal.ZERO.compareTo(card.outstanding()));
        assertEquals(0, BigDecimal.ZERO.compareTo(card.credit()));
        assertEquals(LocalDate.of(2026, 3, 12), card.recent().getFirst().paidOn(),
            "The May session was completed by the March payment, and says so");
    }

    @Test
    @DisplayName("shouldNotReportACreditForSomebodyWhoIsSimplyBehind")
    void shouldNotReportACreditForSomebodyWhoIsSimplyBehind() {
        partiallyPaidSlot(LocalDate.of(2026, 3, 12), client, "150", "100", LocalDate.of(2026, 3, 12));

        SettlementOverviewDto overview = stats.buildOverview("2026", TODAY);

        assertEquals(0, overview.credits().payers());
        assertEquals(0, new BigDecimal("50.00").compareTo(overview.outstanding().total()));
    }

    @Test
    @DisplayName("shouldNotSpendOneGuestsChangeOnAnotherGuestsDebt")
    void shouldNotSpendOneGuestsChangeOnAnotherGuestsDebt() {
        // Two guest bookings are two payers, even under the same written name: a guest has no
        // continuity, so their change has nowhere to go and must not pay a stranger's debt.
        GuestReservation overpaid = guestReservationRepository.saveAndFlush(
            new GuestReservation(pastSlot(LocalDate.of(2026, 3, 12)), "Ekipa z Krakowa", 2));
        guestSettlement(overpaid, "50", "80", LocalDate.of(2026, 3, 12));
        GuestReservation owing = guestReservationRepository.saveAndFlush(
            new GuestReservation(pastSlot(LocalDate.of(2026, 5, 7)), "Ekipa z Krakowa", 2));
        guestSettlement(owing, "200", "0", null);

        SettlementOverviewDto overview = stats.buildOverview("2026", TODAY);

        assertEquals(0, new BigDecimal("200.00").compareTo(overview.outstanding().total()),
            "The guest who owes is listed for the whole two hundred");
        assertEquals(0, new BigDecimal("30.00").compareTo(overview.credits().total()),
            "And the other guest's thirty stays theirs");
    }

    @Test
    @DisplayName("shouldSpendChangeLeftInAYearTheTabIsNotShowing")
    void shouldSpendChangeLeftInAYearTheTabIsNotShowing() {
        // ⚠️ The allocation runs over the whole ledger, never over the year the picker shows —
        // otherwise change left last November would not cover this May's session.
        partiallyPaidSlot(LocalDate.of(2025, 11, 4), client, "50", "100", LocalDate.of(2025, 11, 4));
        partiallyPaidSlot(LocalDate.of(2026, 5, 7), client, "50", "0", null);

        SettlementOverviewDto overview = stats.buildOverview("2026", TODAY);

        assertEquals(0, overview.outstanding().count(),
            "A credit left two Decembers ago still covers today's session");
    }

    @Test
    @DisplayName("shouldListSomebodyHoldingACreditWithNothingOwing")
    void shouldListSomebodyHoldingACreditWithNothingOwing() {
        // ⚠️ The reported gap. Credit used to be computed for debtors only, so this client appeared
        // in NO figure on the tab: not revenue (his money did arrive), not debt (he owes nothing),
        // not the credit note (which only annotates debtors). His fifty was visible solely by
        // opening the session it sits on.
        partiallyPaidSlot(LocalDate.of(2026, 3, 12), client, "150", "200", LocalDate.of(2026, 3, 12));

        CreditsDto credits = stats.buildOverview("2026", TODAY).credits();

        assertEquals(1, credits.payers());
        assertEquals(0, new BigDecimal("50.00").compareTo(credits.total()));
        CreditItemDto item = credits.items().getFirst();
        assertEquals("user", item.payerType());
        assertEquals(client.getId(), item.payerId());
        assertEquals(LocalDate.of(2026, 3, 12), item.date(), "The day the money arrived");
        assertEquals(LocalDate.of(2026, 3, 12), item.targetDate(),
            "And the session it was typed in at — where the owner remembers taking it");
        assertEquals("slot", item.targetType());
        assertEquals(0, new BigDecimal("50.00").compareTo(item.amount()),
            "The unused part of the payment, not what was paid");
        assertEquals(0, credits(stats.buildOverview("2026", TODAY)).compareTo(credits.total()),
            "The heading and the rows add up to the same figure");
    }

    @Test
    @DisplayName("shouldShowAGuestWhoPaidMoreThanTheirSessionCost")
    void shouldShowAGuestWhoPaidMoreThanTheirSessionCost() {
        // A guest's change has nowhere to go — that is what a guest is — but it is still real money
        // we are holding, and the debt list names guests too. Leaving them out here would be the
        // one asymmetry between the two halves of the ledger.
        GuestReservation guest = guestReservationRepository.saveAndFlush(
            new GuestReservation(pastSlot(LocalDate.of(2026, 3, 12)), "Ekipa z Krakowa", 2));
        guestSettlement(guest, "50", "80", LocalDate.of(2026, 3, 12));

        CreditsDto credits = stats.buildOverview("2026", TODAY).credits();

        assertEquals(1, credits.payers());
        assertEquals("guest", credits.items().getFirst().payerType());
        assertEquals(guest.getId(), credits.items().getFirst().payerId());
        assertEquals(0, new BigDecimal("30.00").compareTo(credits.total()));
    }

    @Test
    @DisplayName("shouldListOverpaymentsRegardlessOfTheSelectedYear")
    void shouldListOverpaymentsRegardlessOfTheSelectedYear() {
        // Same policy as the debt list, and for the same reason: money left last December is still
        // sitting here. The card says so above the rows.
        partiallyPaidSlot(LocalDate.of(2024, 11, 4), client, "50", "100", LocalDate.of(2024, 11, 4));

        for (String year : new String[]{"2024", "2026", "all"}) {
            CreditsDto credits = stats.buildOverview(year, TODAY).credits();
            assertEquals(0, new BigDecimal("50.00").compareTo(credits.total()),
                "A credit left in " + year + "'s view is the same credit");
        }
    }

    @Test
    @DisplayName("shouldRankPeopleHoldingTheMostFirst")
    void shouldRankPeopleHoldingTheMostFirst() {
        // The list is a memo to consult when pricing the next session, so the largest sum is the one
        // worth reading first.
        partiallyPaidSlot(LocalDate.of(2026, 3, 12), client, "50", "70", LocalDate.of(2026, 3, 12));
        partiallyPaidSlot(LocalDate.of(2026, 4, 2), other, "50", "200", LocalDate.of(2026, 4, 2));

        CreditsDto credits = stats.buildOverview("2026", TODAY).credits();

        assertEquals(2, credits.payers());
        assertEquals(other.getId(), credits.items().getFirst().payerId(),
            "150 outranks 20");
        assertEquals(0, new BigDecimal("170.00").compareTo(credits.total()));
    }

    /** Sum of the per-payer credit the card would draw, to check the header against the rows. */
    private BigDecimal credits(SettlementOverviewDto overview) {
        BigDecimal sum = BigDecimal.ZERO;
        for (CreditItemDto item : overview.credits().items()) {
            sum = sum.add(item.amount());
        }
        return sum;
    }

    @Test
    @DisplayName("shouldAgreeWithTheSettlementsTabAboutWhatOneClientOwes")
    void shouldAgreeWithTheSettlementsTabAboutWhatOneClientOwes() {
        // The point is not either figure on its own — it is that the two screens cannot disagree.
        partiallyPaidSlot(LocalDate.of(2026, 3, 12), client, "150", "100", LocalDate.of(2026, 3, 12));
        partiallyPaidSlot(LocalDate.of(2026, 4, 2), client, "80", "0", null);

        PayerSummaryDto card = stats.payerSummary(client.getId(), 10);
        SettlementOverviewDto overview = stats.buildOverview("2026", TODAY);

        assertEquals(0, overview.outstanding().total().compareTo(card.outstanding()),
            "The tab chases 130 and so must the card");
        assertEquals(0, overview.revenue().total().compareTo(card.paid()),
            "And they agree on what came in, too");
    }

    /*
     * Written straight into the tables: these tests are about the arithmetic on top, and the write
     * path has its own coverage in {@code AdminSettlementIntegrationTest}. Going through the service
     * would also mean fabricating a confirmed booking for every row. Each helper writes the CHARGE and,
     * when money arrived, one PAYMENT typed in at the same target — the shape the modal produces.
     */

    /** A standing coaching fee: a settlement whose target is a month, with no calendar entry at all. */
    private void monthlyFee(LocalDate month, User payer, String amount, LocalDate paidOn) {
        jdbc.update("INSERT INTO settlements (period_month, user_id, amount) VALUES (?, ?, ?)",
            month, payer.getId(), new BigDecimal(amount));
        if (paidOn != null) {
            jdbc.update("INSERT INTO payments (user_id, amount, received_on) VALUES (?, ?, ?)",
                payer.getId(), new BigDecimal(amount), paidOn);
        }
    }

    private void partiallyPaidSlot(LocalDate on, User payer, String amount, String paid, LocalDate paidOn) {
        TimeSlot slot = timeSlotRepository.saveAndFlush(
            new TimeSlot(on, LocalTime.of(18, 0), LocalTime.of(20, 0), 4));
        jdbc.update("INSERT INTO settlements (time_slot_id, user_id, amount) VALUES (?, ?, ?)",
            slot.getId(), payer.getId(), new BigDecimal(amount));
        if (paidOn != null && new BigDecimal(paid).signum() > 0) {
            jdbc.update("INSERT INTO payments (user_id, amount, received_on, entered_slot_id) "
                    + "VALUES (?, ?, ?, ?)",
                payer.getId(), new BigDecimal(paid), paidOn, slot.getId());
        }
    }

    /** A guest's own row. Unique on the guest alone, so there is never a second one to pair it with. */
    private void guestSettlement(GuestReservation guest, String amount, String paid, LocalDate paidOn) {
        jdbc.update("INSERT INTO settlements (time_slot_id, guest_reservation_id, amount) VALUES (?, ?, ?)",
            guest.getTimeSlot().getId(), guest.getId(), new BigDecimal(amount));
        if (paidOn != null && new BigDecimal(paid).signum() > 0) {
            jdbc.update("INSERT INTO payments (guest_reservation_id, amount, received_on, entered_slot_id) "
                    + "VALUES (?, ?, ?, ?)",
                guest.getId(), new BigDecimal(paid), paidOn, guest.getTimeSlot().getId());
        }
    }

    private void settleSlot(LocalDate on, User payer, String amount, LocalDate paidOn) {
        partiallyPaidSlot(on, payer, amount, amount, paidOn);
    }

    private void settleEvent(LocalDate from, LocalDate to, User payer, String amount, LocalDate paidOn) {
        Event event = eventRepository.saveAndFlush(
            new Event("Kurs", EventType.COURSE, from, to, 8));
        jdbc.update("INSERT INTO settlements (event_id, user_id, amount) VALUES (?, ?, ?)",
            event.getId(), payer.getId(), new BigDecimal(amount));
        if (paidOn != null) {
            jdbc.update("INSERT INTO payments (user_id, amount, received_on, entered_event_id) "
                    + "VALUES (?, ?, ?, ?)",
                payer.getId(), new BigDecimal(amount), paidOn, event.getId());
        }
    }

    private BigDecimal monthOf(SettlementOverviewDto overview, LocalDate month) {
        return overview.revenue().months().stream()
            .filter(entry -> entry.month().equals(month))
            .map(MonthlyRevenueDto::amount)
            .findFirst()
            .orElse(BigDecimal.ZERO);
    }
}
