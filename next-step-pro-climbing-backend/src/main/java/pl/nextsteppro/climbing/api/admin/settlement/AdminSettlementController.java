package pl.nextsteppro.climbing.api.admin.settlement;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * What each participant owes for a session, and whether they have paid.
 *
 * <p>Admin-only and nothing else reads it: money about named people never travels in the shared
 * calendar DTOs, which are served to anonymous visitors and cached. Under {@code /api/admin}, so the
 * {@code admin} rate-limit bucket and the {@code hasRole('ADMIN')} matcher in
 * {@code SecurityConfig} already cover it.
 *
 * <p>Both the target kind and the payer kind are path segments —
 * {@code /api/admin/settlements/{slot|event}/{id}/{user|guest}/{id}} — so the four combinations run
 * on one code path rather than as four near-identical endpoint families. The slot/event twinning in
 * this codebase has repeatedly failed the same way: the fix lands in one copy.
 *
 * <p>⚠️ There is no {@code reservation} target, and adding one would be a bug: a multi-day event
 * books one reservation row per day, so pricing per reservation charges a three-day course three
 * times.
 */
@RestController
@RequestMapping("/api/admin/settlements")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Settlements", description = "Per-participant price and payment status for a session — never visible to clients")
public class AdminSettlementController {

    /** Enough to say what the balance is made of without becoming a second Settlements tab. */
    private static final int RECENT_PAYER_LINES = 8;

    private final AdminSettlementService settlementService;
    private final AdminSettlementStatsService statsService;
    private final AdminPayoutService payoutService;
    private final AdminSubscriptionService subscriptionService;

    public AdminSettlementController(AdminSettlementService settlementService,
                                     AdminSettlementStatsService statsService,
                                     AdminPayoutService payoutService,
                                     AdminSubscriptionService subscriptionService) {
        this.settlementService = settlementService;
        this.statsService = statsService;
        this.payoutService = payoutService;
        this.subscriptionService = subscriptionService;
    }

    // ----- bulk payers: work somebody else settles for a whole month at once -----

    @Operation(summary = "List bulk payers",
        description = "Schools, clubs and the like. Archived ones are included and flagged: the tab "
            + "still has to name the source of money earned last season.")
    @GetMapping("/sources")
    public ResponseEntity<List<PayoutSourceDto>> listSources() {
        return ResponseEntity.ok(payoutService.listSources());
    }

    @Operation(summary = "Add a bulk payer")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Created"),
        @ApiResponse(responseCode = "400", description = "Blank name, or a name an active payer already uses")
    })
    @PostMapping("/sources")
    public ResponseEntity<PayoutSourceDto> createSource(@Valid @RequestBody SavePayoutSourceRequest request) {
        return ResponseEntity.ok(payoutService.createSource(request));
    }

    @Operation(summary = "Rename a bulk payer")
    @PutMapping("/sources/{sourceId}")
    public ResponseEntity<Void> renameSource(@PathVariable UUID sourceId,
                                             @Valid @RequestBody SavePayoutSourceRequest request) {
        payoutService.renameSource(sourceId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Archive or restore a bulk payer",
        description = "Archived rather than deleted: transfers and session assignments point at it, "
            + "and a collaboration that ended does not un-earn what it paid.")
    @PutMapping("/sources/{sourceId}/archived")
    public ResponseEntity<Void> setSourceArchived(@PathVariable UUID sourceId,
                                                  @RequestParam boolean archived) {
        payoutService.setSourceArchived(sourceId, archived);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mark a session as work for a bulk payer",
        description = "Send a null sourceId to unmark it. A marked session is not priced per "
            + "participant — there is nobody to charge — so it stays out of the pricing queue.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Saved"),
        @ApiResponse(responseCode = "400", description = "Unknown target, a slot belonging to an event, or an unknown payer")
    })
    @PutMapping("/{targetType}/{targetId}/payout-source")
    public ResponseEntity<Void> assignSource(
            @Parameter(description = "slot or event") @PathVariable String targetType,
            @PathVariable UUID targetId,
            @Valid @RequestBody AssignPayoutSourceRequest request) {
        payoutService.assignSource(targetType, targetId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Record a transfer that arrived",
        description = "periodMonth is any day of the month the work was done in; receivedOn is when "
            + "the money landed. Revenue counts on the second, the derived rate on the first.")
    @PostMapping("/payouts")
    public ResponseEntity<UUID> createPayout(@Valid @RequestBody SavePayoutRequest request) {
        return ResponseEntity.ok(payoutService.createPayout(request));
    }

    @Operation(summary = "Delete a transfer")
    @DeleteMapping("/payouts/{payoutId}")
    public ResponseEntity<Void> deletePayout(@PathVariable UUID payoutId) {
        payoutService.deletePayout(payoutId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Settle everything one payer owes",
        description = "One date for the whole batch, because one transfer covered it. Taking each "
            + "session's own day instead would scatter a single payment across the months it paid "
            + "for. Only unsettled rows are touched, so running it twice changes nothing.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "How many amounts were settled"),
        @ApiResponse(responseCode = "400", description = "Unknown payer type")
    })
    @PostMapping("/settle-outstanding")
    public ResponseEntity<SettleOutstandingResultDto> settleOutstanding(
            @Valid @RequestBody SettleOutstandingRequest request) {
        return ResponseEntity.ok(new SettleOutstandingResultDto(
            settlementService.settleOutstanding(request)));
    }

    // ----- standing monthly coaching fees -----

    @Operation(summary = "This client's coaching subscriptions",
        description = "Newest first, ended ones included: the months they billed still point at them.")
    @GetMapping("/subscriptions/{userId}")
    public ResponseEntity<List<SubscriptionDto>> subscriptions(@PathVariable UUID userId) {
        return ResponseEntity.ok(subscriptionService.forUser(userId));
    }

    @Operation(summary = "Start a coaching subscription",
        description = "Bills every month it already covers straight away, rather than waiting for "
            + "the nightly run — somebody adding a retainer that started in March expects March.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Started"),
        @ApiResponse(responseCode = "400", description = "Another one is already running for this person, or the end precedes the start")
    })
    @PostMapping("/subscriptions/{userId}")
    public ResponseEntity<SubscriptionDto> createSubscription(
            @PathVariable UUID userId, @Valid @RequestBody SaveSubscriptionRequest request) {
        return ResponseEntity.ok(subscriptionService.create(userId, request));
    }

    @Operation(summary = "Change what a subscription charges",
        description = "Forward-only: months already billed keep what they were billed at, because a "
            + "raise in June is not a claim about March.")
    @PutMapping("/subscriptions/{subscriptionId}/amount")
    public ResponseEntity<Void> changeSubscriptionAmount(
            @PathVariable UUID subscriptionId, @Valid @RequestBody SaveSubscriptionRequest request) {
        subscriptionService.changeAmount(subscriptionId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "End a subscription",
        description = "The month may be in the past. A backdated end drops the fees for months "
            + "beginning after it, but only the UNPAID ones — a paid fee stays, because the money "
            + "arrived and a date written down late does not overrule the bank.")
    @PutMapping("/subscriptions/{subscriptionId}/end")
    public ResponseEntity<Void> endSubscription(
            @PathVariable UUID subscriptionId, @Valid @RequestBody EndSubscriptionRequest request) {
        subscriptionService.end(subscriptionId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reopen a subscription closed by mistake")
    @PutMapping("/subscriptions/{subscriptionId}/reopen")
    public ResponseEntity<Void> reopenSubscription(@PathVariable UUID subscriptionId) {
        subscriptionService.reopen(subscriptionId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete a subscription",
        description = "Removes the rule. Fees it already billed stay: they are somebody's debts or "
            + "somebody's payments, and neither stops being one because the rule went away.")
    @DeleteMapping("/subscriptions/{subscriptionId}")
    public ResponseEntity<Void> deleteSubscription(@PathVariable UUID subscriptionId) {
        subscriptionService.delete(subscriptionId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "One client's money, for their user card",
        description = "Whole history, not the tab's selected year — this answers 'what do I have "
            + "with this person', which has no year in it. A second request from the browser rather "
            + "than a field on the user card's own DTO: the settlement types are unreachable from "
            + "that package by design, and one rule beats an exception to it.")
    @GetMapping("/payers/user/{userId}")
    public ResponseEntity<PayerSummaryDto> payerSummary(@PathVariable UUID userId) {
        return ResponseEntity.ok(statsService.payerSummary(userId, RECENT_PAYER_LINES));
    }

    @Operation(summary = "Every income line of a year, for the accountant",
        description = "Line items rather than the tab's aggregates, and its own endpoint so the "
            + "common read does not pay for the rare one. Unpaid lines are included with an empty "
            + "payment date — what is still owed is the other half of the same conversation.")
    @GetMapping("/export")
    public ResponseEntity<List<SettlementExportRowDto>> exportRows(
            @Parameter(description = "A four-digit year, 'all', or omitted for the newest year holding data")
            @RequestParam(required = false) String year,
            @Parameter(description = "Label for a client's own fee, translated by the caller")
            @RequestParam String clientKind,
            @Parameter(description = "Label for a bulk transfer, translated by the caller")
            @RequestParam String payoutKind) {
        return ResponseEntity.ok(statsService.exportRows(year, clientKind, payoutKind));
    }

    @Operation(summary = "The Settlements tab",
        description = "Outstanding debt, revenue by month, and a per-person breakdown, from one "
            + "read. Outstanding debt deliberately covers the WHOLE history and ignores the year "
            + "filter — a debt from two years ago is still a debt.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The overview"),
        @ApiResponse(responseCode = "400", description = "Unparseable year")
    })
    @GetMapping("/overview")
    public ResponseEntity<SettlementOverviewDto> getOverview(
            @Parameter(description = "A four-digit year, 'all' for everything, or omitted for the newest year holding data")
            @RequestParam(required = false) String year) {
        return ResponseEntity.ok(statsService.getOverview(year));
    }

    @Operation(summary = "Who can be charged for this session, and for how much",
        description = "Confirmed bookings and guests, each with the amount saved so far. A payer "
            + "whose booking has since been cancelled still appears, flagged as orphaned — money "
            + "that changed hands does not stop having changed hands. An event returns one line per "
            + "person however many days it spans.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "The payers and their amounts"),
        @ApiResponse(responseCode = "400", description = "Unknown target type, missing target, or a slot that belongs to an event")
    })
    @GetMapping("/{targetType}/{targetId}")
    public ResponseEntity<SettlementSectionDto> getSection(
            @Parameter(description = "slot or event") @PathVariable String targetType,
            @PathVariable UUID targetId) {
        return ResponseEntity.ok(settlementService.getSection(targetType, targetId));
    }

    @Operation(summary = "Set what one payer owes, and whether they have paid",
        description = "Idempotent upsert. Omit settledOn to leave the amount outstanding; send a "
            + "date to record the payment into that month. An event is priced once however many "
            + "days it spans, so a slot that belongs to an event is refused — price the event.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Saved"),
        @ApiResponse(responseCode = "400", description = "Amount out of range, unknown target or payer type, a slot that belongs to an event, or a payer with no booking on this session")
    })
    @PutMapping("/{targetType}/{targetId}/{payerType}/{payerId}")
    public ResponseEntity<Void> save(
            @Parameter(description = "slot or event") @PathVariable String targetType,
            @PathVariable UUID targetId,
            @Parameter(description = "user or guest") @PathVariable String payerType,
            @PathVariable UUID payerId,
            @Valid @RequestBody SaveSettlementRequest request) {
        settlementService.save(targetType, targetId, payerType, payerId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Drop one payer's amount",
        description = "Idempotent — removing an amount that is not there succeeds. Leaves the payer "
            + "unpriced, which is a different state from priced at zero.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Removed, or there was nothing to remove"),
        @ApiResponse(responseCode = "400", description = "Unknown target or payer type")
    })
    @DeleteMapping("/{targetType}/{targetId}/{payerType}/{payerId}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "slot or event") @PathVariable String targetType,
            @PathVariable UUID targetId,
            @Parameter(description = "user or guest") @PathVariable String payerType,
            @PathVariable UUID payerId) {
        settlementService.delete(targetType, targetId, payerType, payerId);
        return ResponseEntity.noContent().build();
    }
}
