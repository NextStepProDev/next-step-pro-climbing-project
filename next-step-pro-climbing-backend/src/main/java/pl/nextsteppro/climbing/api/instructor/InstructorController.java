package pl.nextsteppro.climbing.api.instructor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.nextsteppro.climbing.api.instructor.InstructorDtos.InstructorPublicDto;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/instructors")
@Tag(name = "Instructors", description = "Public access to instructor information")
public class InstructorController {

    private final InstructorService instructorService;

    public InstructorController(InstructorService instructorService) {
        this.instructorService = instructorService;
    }

    @Operation(
        summary = "Get active instructors",
        description = "Returns all active instructors in the given language, sorted by display order"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of instructors",
            content = @Content(schema = @Schema(implementation = InstructorPublicDto.class)))
    })
    @GetMapping
    public ResponseEntity<List<InstructorPublicDto>> getAllInstructors(
            @Parameter(description = "Content language (pl, en, es)")
            @RequestParam(defaultValue = "pl") String language) {
        List<InstructorPublicDto> instructors = instructorService.getAllActiveInstructors(language);
        return ResponseEntity.ok(instructors);
    }

    @Operation(
        summary = "Get instructor details",
        description = "Returns detailed information about a specific instructor"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Instructor details",
            content = @Content(schema = @Schema(implementation = InstructorPublicDto.class))),
        @ApiResponse(responseCode = "404", description = "Instructor not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<InstructorPublicDto> getInstructor(
            @Parameter(description = "ID instruktora") @PathVariable UUID id) {
        InstructorPublicDto instructor = instructorService.getInstructor(id);
        return ResponseEntity.ok(instructor);
    }
}
