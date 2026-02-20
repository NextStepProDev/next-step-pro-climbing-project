package pl.nextsteppro.climbing.api.calendar;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.nextsteppro.climbing.config.CurrentUserId;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

@RestController
@RequestMapping("/api/calendar")
@Tag(name = "Calendar", description = "Publiczny kalendarz z dostępnymi terminami zajęć")
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @Operation(
        summary = "Widok miesiąca",
        description = "Zwraca podsumowanie dostępności terminów dla każdego dnia w miesiącu oraz listę wydarzeń"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dane miesiąca",
            content = @Content(schema = @Schema(implementation = MonthViewDto.class))),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowy format daty")
    })
    @GetMapping("/month/{yearMonth}")
    public ResponseEntity<MonthViewDto> getMonthView(
            @Parameter(description = "Miesiąc w formacie yyyy-MM", example = "2026-02")
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM") YearMonth yearMonth,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        MonthViewDto view = calendarService.getMonthView(yearMonth, userId);
        return ResponseEntity.ok(view);
    }

    @Operation(
        summary = "Widok tygodnia",
        description = "Zwraca listę terminów dla każdego dnia w tygodniu zaczynającym się od podanej daty (poniedziałek)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dane tygodnia",
            content = @Content(schema = @Schema(implementation = WeekViewDto.class))),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowy format daty")
    })
    @GetMapping("/week/{date}")
    public ResponseEntity<WeekViewDto> getWeekView(
            @Parameter(description = "Data poniedziałku w formacie yyyy-MM-dd", example = "2026-02-16")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        WeekViewDto view = calendarService.getWeekView(date, userId);
        return ResponseEntity.ok(view);
    }

    @Operation(
        summary = "Widok dnia",
        description = "Zwraca szczegółową listę terminów dla wybranego dnia z informacją o dostępności"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dane dnia",
            content = @Content(schema = @Schema(implementation = DayViewDto.class))),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowy format daty")
    })
    @GetMapping("/day/{date}")
    public ResponseEntity<DayViewDto> getDayView(
            @Parameter(description = "Data w formacie yyyy-MM-dd", example = "2026-02-07")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        DayViewDto view = calendarService.getDayView(date, userId);
        return ResponseEntity.ok(view);
    }

    @Operation(
        summary = "Szczegóły wydarzenia",
        description = "Zwraca pełne informacje o wydarzeniu: typ, daty, liczba miejsc, status zapisu użytkownika"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Szczegóły wydarzenia",
            content = @Content(schema = @Schema(implementation = EventSummaryDto.class))),
        @ApiResponse(responseCode = "404", description = "Wydarzenie nie istnieje")
    })
    @GetMapping("/event/{eventId}")
    public ResponseEntity<EventSummaryDto> getEventSummary(
            @Parameter(description = "UUID wydarzenia") @PathVariable UUID eventId,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        EventSummaryDto summary = calendarService.getEventSummary(eventId, userId);
        return ResponseEntity.ok(summary);
    }

    @Operation(
        summary = "Szczegóły terminu",
        description = "Zwraca pełne informacje o terminie: godziny, liczba miejsc, status rezerwacji użytkownika"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Szczegóły terminu",
            content = @Content(schema = @Schema(implementation = TimeSlotDetailDto.class))),
        @ApiResponse(responseCode = "404", description = "Termin nie istnieje")
    })
    @GetMapping("/slot/{slotId}")
    public ResponseEntity<TimeSlotDetailDto> getSlotDetails(
            @Parameter(description = "UUID terminu") @PathVariable UUID slotId,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        TimeSlotDetailDto details = calendarService.getSlotDetails(slotId, userId);
        return ResponseEntity.ok(details);
    }
}
