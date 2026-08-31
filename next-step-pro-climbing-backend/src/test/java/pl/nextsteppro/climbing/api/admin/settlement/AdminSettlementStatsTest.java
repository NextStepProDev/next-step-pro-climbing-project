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
import java.util.UUID;

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
        assertEquals(2, top.settlementCount());
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
        jdbc.update("INSERT INTO settlements (event_id, guest_reservation_id, amount, settled_on) "
            + "VALUES (?, ?, 1800, ?)", event.getId(), guestId, LocalDate.of(2026, 7, 1));

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

    // ------------------------------------------------------------------ fixtures

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

    /**
     * Written straight into the table: these tests are about the arithmetic on top, and the write
     * path has its own coverage in {@code AdminSettlementIntegrationTest}. Going through the service
     * would also mean fabricating a confirmed booking for every row.
     */
    private void settleSlot(LocalDate on, User payer, String amount, LocalDate settledOn) {
        TimeSlot slot = timeSlotRepository.saveAndFlush(
            new TimeSlot(on, LocalTime.of(18, 0), LocalTime.of(20, 0), 4));
        jdbc.update("INSERT INTO settlements (time_slot_id, user_id, amount, settled_on) VALUES (?, ?, ?, ?)",
            slot.getId(), payer.getId(), new BigDecimal(amount), settledOn);
    }

    private void settleEvent(LocalDate from, LocalDate to, User payer, String amount, LocalDate settledOn) {
        Event event = eventRepository.saveAndFlush(
            new Event("Kurs", EventType.COURSE, from, to, 8));
        jdbc.update("INSERT INTO settlements (event_id, user_id, amount, settled_on) VALUES (?, ?, ?, ?)",
            event.getId(), payer.getId(), new BigDecimal(amount), settledOn);
    }

    private BigDecimal monthOf(SettlementOverviewDto overview, LocalDate month) {
        return overview.revenue().months().stream()
            .filter(entry -> entry.month().equals(month))
            .map(MonthlyRevenueDto::amount)
            .findFirst()
            .orElse(BigDecimal.ZERO);
    }
}
