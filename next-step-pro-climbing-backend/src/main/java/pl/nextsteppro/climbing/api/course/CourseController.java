package pl.nextsteppro.climbing.api.course;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.nextsteppro.climbing.api.course.CourseDtos.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/courses")
@Tag(name = "Courses", description = "Public access to courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @Operation(summary = "Get published courses")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of courses")
    })
    @GetMapping
    public ResponseEntity<List<CourseSummaryDto>> getAll(
            @Parameter(description = "Course content language (pl, en, es)")
            @RequestParam(defaultValue = "pl") String language) {
        return ResponseEntity.ok(courseService.getAllPublished(language));
    }

    @Operation(summary = "Get course details with content blocks")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Course details"),
        @ApiResponse(responseCode = "400", description = "Course not found or unpublished")
    })
    @GetMapping("/{id}")
    public ResponseEntity<CourseDetailDto> getById(
            @Parameter(description = "ID kursu") @PathVariable UUID id) {
        return ResponseEntity.ok(courseService.getPublishedById(id));
    }

    @Operation(summary = "Get available language versions of the course")
    @GetMapping("/by-group/{translationGroupId}")
    public ResponseEntity<List<CourseTranslationDto>> getTranslations(
            @Parameter(description = "Translation group ID") @PathVariable UUID translationGroupId) {
        return ResponseEntity.ok(courseService.getAvailableTranslations(translationGroupId));
    }
}
