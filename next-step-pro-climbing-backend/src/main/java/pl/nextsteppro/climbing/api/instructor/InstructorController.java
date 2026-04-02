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
@Tag(name = "Instructors", description = "Publiczny dostęp do informacji o instruktorach")
public class InstructorController {

    private final InstructorService instructorService;

    public InstructorController(InstructorService instructorService) {
        this.instructorService = instructorService;
    }

    @Operation(
        summary = "Pobierz listę aktywnych instruktorów",
        description = "Zwraca listę wszystkich aktywnych instruktorów, posortowaną według kolejności wyświetlania"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista instruktorów",
            content = @Content(schema = @Schema(implementation = InstructorPublicDto.class)))
    })
    @GetMapping
    public ResponseEntity<List<InstructorPublicDto>> getAllInstructors() {
        List<InstructorPublicDto> instructors = instructorService.getAllActiveInstructors();
        return ResponseEntity.ok(instructors);
    }

    @Operation(
        summary = "Pobierz szczegóły instruktora",
        description = "Zwraca szczegółowe informacje o konkretnym instruktorze"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Szczegóły instruktora",
            content = @Content(schema = @Schema(implementation = InstructorPublicDto.class))),
        @ApiResponse(responseCode = "404", description = "Instruktor nie znaleziony")
    })
    @GetMapping("/{id}")
    public ResponseEntity<InstructorPublicDto> getInstructor(
            @Parameter(description = "ID instruktora") @PathVariable UUID id) {
        InstructorPublicDto instructor = instructorService.getInstructor(id);
        return ResponseEntity.ok(instructor);
    }
}
