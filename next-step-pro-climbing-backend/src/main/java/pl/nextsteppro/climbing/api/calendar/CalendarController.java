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

import java.util.List;
import pl.nextsteppro.climbing.config.CurrentUserId;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

@RestController
@RequestMapping("/api/calendar")
@Tag(name = "Calendar", description = "Public calendar with available class slots")
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @Operation(
        summary = "Month view",
        description = "Returns slot availability summary for each day of the month plus the list of events"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Month data",
            content = @Content(schema = @Schema(implementation = MonthViewDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid date format")
    })
    @GetMapping("/month/{yearMonth}")
    public ResponseEntity<MonthViewDto> getMonthView(
            @Parameter(description = "Month in yyyy-MM format", example = "2026-02")
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM") YearMonth yearMonth,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        MonthViewDto view = calendarService.getMonthView(yearMonth, userId);
        return ResponseEntity.ok(view);
    }

    @Operation(
        summary = "Week view",
        description = "Returns the slot list for each day of the week starting from the given date (Monday)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Week data",
            content = @Content(schema = @Schema(implementation = WeekViewDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid date format")
    })
    @GetMapping("/week/{date}")
    public ResponseEntity<WeekViewDto> getWeekView(
            @Parameter(description = "Monday date in yyyy-MM-dd format", example = "2026-02-16")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        WeekViewDto view = calendarService.getWeekView(date, userId);
        return ResponseEntity.ok(view);
    }

    @Operation(
        summary = "Day view",
        description = "Returns a detailed slot list for the chosen day with availability info"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Day data",
            content = @Content(schema = @Schema(implementation = DayViewDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid date format")
    })
    @GetMapping("/day/{date}")
    public ResponseEntity<DayViewDto> getDayView(
            @Parameter(description = "Date in yyyy-MM-dd format", example = "2026-02-07")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        DayViewDto view = calendarService.getDayView(date, userId);
        return ResponseEntity.ok(view);
    }

    @Operation(
        summary = "Event details",
        description = "Returns full event information: type, dates, seat count, user registration status"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Event details",
            content = @Content(schema = @Schema(implementation = EventSummaryDto.class))),
        @ApiResponse(responseCode = "404", description = "Event not found")
    })
    @GetMapping("/event/{eventId}")
    public ResponseEntity<EventSummaryDto> getEventSummary(
            @Parameter(description = "Event UUID") @PathVariable UUID eventId,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        EventSummaryDto summary = calendarService.getEventSummary(eventId, userId);
        return ResponseEntity.ok(summary);
    }

    @Operation(
        summary = "Slot details",
        description = "Returns full slot information: times, seat count, user reservation status"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Slot details",
            content = @Content(schema = @Schema(implementation = TimeSlotDetailDto.class))),
        @ApiResponse(responseCode = "404", description = "Slot not found")
    })
    @GetMapping("/slot/{slotId}")
    public ResponseEntity<TimeSlotDetailDto> getSlotDetails(
            @Parameter(description = "Slot UUID") @PathVariable UUID slotId,
            @Parameter(hidden = true) @CurrentUserId UUID userId) {

        TimeSlotDetailDto details = calendarService.getSlotDetails(slotId, userId);
        return ResponseEntity.ok(details);
    }

    @Operation(
        summary = "Course dates",
        description = "Returns upcoming events linked to the given course"
    )
    @GetMapping("/course/{courseId}/events")
    public ResponseEntity<List<CourseEventDto>> getCourseEvents(
            @Parameter(description = "Course UUID") @PathVariable UUID courseId) {

        List<CourseEventDto> events = calendarService.getCourseEvents(courseId);
        return ResponseEntity.ok(events);
    }

    @Operation(
        summary = "Course translation group dates",
        description = "Returns upcoming events for all language versions of the course"
    )
    @GetMapping("/course-group/{translationGroupId}/events")
    public ResponseEntity<List<CourseEventDto>> getCourseEventsByTranslationGroup(
            @Parameter(description = "Translation group UUID") @PathVariable UUID translationGroupId) {

        List<CourseEventDto> events = calendarService.getCourseEventsByTranslationGroup(translationGroupId);
        return ResponseEntity.ok(events);
    }
}
