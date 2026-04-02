package pl.nextsteppro.climbing.api.admin.instructor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.api.admin.instructor.AdminInstructorDtos.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/instructors")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Instructors", description = "Zarządzanie instruktorami (tylko admin)")
public class AdminInstructorController {

    private final AdminInstructorService adminInstructorService;

    public AdminInstructorController(AdminInstructorService adminInstructorService) {
        this.adminInstructorService = adminInstructorService;
    }

    @Operation(
        summary = "Pobierz wszystkich instruktorów",
        description = "Zwraca listę wszystkich instruktorów (łącznie z nieaktywnymi)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista instruktorów",
            content = @Content(schema = @Schema(implementation = InstructorAdminDto.class))),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @GetMapping
    public ResponseEntity<List<InstructorAdminDto>> getAllInstructors() {
        List<InstructorAdminDto> instructors = adminInstructorService.getAllInstructors();
        return ResponseEntity.ok(instructors);
    }

    @Operation(
        summary = "Pobierz szczegóły instruktora",
        description = "Zwraca szczegółowe informacje o instruktorze"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Szczegóły instruktora",
            content = @Content(schema = @Schema(implementation = InstructorAdminDto.class))),
        @ApiResponse(responseCode = "404", description = "Instruktor nie znaleziony"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @GetMapping("/{id}")
    public ResponseEntity<InstructorAdminDto> getInstructor(
            @Parameter(description = "ID instruktora") @PathVariable UUID id) {
        InstructorAdminDto instructor = adminInstructorService.getInstructor(id);
        return ResponseEntity.ok(instructor);
    }

    @Operation(
        summary = "Utwórz instruktora",
        description = "Dodaje nowego instruktora do systemu"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Instruktor utworzony",
            content = @Content(schema = @Schema(implementation = InstructorAdminDto.class))),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowe dane"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @PostMapping
    public ResponseEntity<InstructorAdminDto> createInstructor(
            @Valid @RequestBody CreateInstructorRequest request) {
        InstructorAdminDto instructor = adminInstructorService.createInstructor(request);
        return ResponseEntity.ok(instructor);
    }

    @Operation(
        summary = "Aktualizuj instruktora",
        description = "Aktualizuje dane instruktora"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Instruktor zaktualizowany",
            content = @Content(schema = @Schema(implementation = InstructorAdminDto.class))),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowe dane"),
        @ApiResponse(responseCode = "404", description = "Instruktor nie znaleziony"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @PutMapping("/{id}")
    public ResponseEntity<InstructorAdminDto> updateInstructor(
            @Parameter(description = "ID instruktora") @PathVariable UUID id,
            @Valid @RequestBody UpdateInstructorRequest request) {
        InstructorAdminDto instructor = adminInstructorService.updateInstructor(id, request);
        return ResponseEntity.ok(instructor);
    }

    @Operation(
        summary = "Usuń instruktora",
        description = "Usuwa instruktora z systemu (wraz ze zdjęciem)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Instruktor usunięty"),
        @ApiResponse(responseCode = "404", description = "Instruktor nie znaleziony"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInstructor(
            @Parameter(description = "ID instruktora") @PathVariable UUID id) {
        adminInstructorService.deleteInstructor(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Prześlij zdjęcie instruktora",
        description = "Dodaje lub zastępuje zdjęcie instruktora (max 10MB, JPEG/PNG/WebP)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Zdjęcie przesłane"),
        @ApiResponse(responseCode = "400", description = "Nieprawidłowy plik"),
        @ApiResponse(responseCode = "404", description = "Instruktor nie znaleziony"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @PostMapping("/{id}/photo")
    public ResponseEntity<Void> uploadPhoto(
            @Parameter(description = "ID instruktora") @PathVariable UUID id,
            @Parameter(description = "Plik zdjęcia") @RequestParam("file") MultipartFile file) throws IOException {
        adminInstructorService.uploadPhoto(id, file);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Usuń zdjęcie instruktora",
        description = "Usuwa zdjęcie instruktora"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Zdjęcie usunięte"),
        @ApiResponse(responseCode = "404", description = "Instruktor nie znaleziony lub brak zdjęcia"),
        @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora")
    })
    @DeleteMapping("/{id}/photo")
    public ResponseEntity<Void> deletePhoto(
            @Parameter(description = "ID instruktora") @PathVariable UUID id) throws IOException {
        adminInstructorService.deletePhoto(id);
        return ResponseEntity.noContent().build();
    }
}
