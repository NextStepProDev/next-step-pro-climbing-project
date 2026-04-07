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
@Tag(name = "Courses", description = "Publiczny dostęp do kursów")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @Operation(summary = "Pobierz listę opublikowanych kursów")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista kursów")
    })
    @GetMapping
    public ResponseEntity<List<CourseSummaryDto>> getAll() {
        return ResponseEntity.ok(courseService.getAllPublished());
    }

    @Operation(summary = "Pobierz szczegóły kursu z blokami treści")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Szczegóły kursu"),
        @ApiResponse(responseCode = "400", description = "Kurs nie znaleziony lub nieopublikowany")
    })
    @GetMapping("/{id}")
    public ResponseEntity<CourseDetailDto> getById(
            @Parameter(description = "ID kursu") @PathVariable UUID id) {
        return ResponseEntity.ok(courseService.getPublishedById(id));
    }
}
