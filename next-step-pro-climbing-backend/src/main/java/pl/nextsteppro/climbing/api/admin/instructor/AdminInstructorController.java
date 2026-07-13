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
@Tag(name = "Admin - Instructors", description = "Instructor management (admin only)")
public class AdminInstructorController {

    private final AdminInstructorService adminInstructorService;

    public AdminInstructorController(AdminInstructorService adminInstructorService) {
        this.adminInstructorService = adminInstructorService;
    }

    @Operation(
        summary = "Get all instructors",
        description = "Returns all instructors (including inactive ones)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of instructors",
            content = @Content(schema = @Schema(implementation = InstructorAdminDto.class))),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @GetMapping
    public ResponseEntity<List<InstructorAdminDto>> getAllInstructors() {
        List<InstructorAdminDto> instructors = adminInstructorService.getAllInstructors();
        return ResponseEntity.ok(instructors);
    }

    @Operation(
        summary = "Get instructor details",
        description = "Returns detailed instructor information"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Instructor details",
            content = @Content(schema = @Schema(implementation = InstructorAdminDto.class))),
        @ApiResponse(responseCode = "404", description = "Instructor not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @GetMapping("/{id}")
    public ResponseEntity<InstructorAdminDto> getInstructor(
            @Parameter(description = "ID instruktora") @PathVariable UUID id) {
        InstructorAdminDto instructor = adminInstructorService.getInstructor(id);
        return ResponseEntity.ok(instructor);
    }

    @Operation(
        summary = "Create instructor",
        description = "Adds a new instructor to the system"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Instructor created",
            content = @Content(schema = @Schema(implementation = InstructorAdminDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid data"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PostMapping
    public ResponseEntity<InstructorAdminDto> createInstructor(
            @Valid @RequestBody CreateInstructorRequest request) {
        InstructorAdminDto instructor = adminInstructorService.createInstructor(request);
        return ResponseEntity.ok(instructor);
    }

    @Operation(
        summary = "Update instructor",
        description = "Updates instructor data"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Instructor updated",
            content = @Content(schema = @Schema(implementation = InstructorAdminDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid data"),
        @ApiResponse(responseCode = "404", description = "Instructor not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PutMapping("/{id}")
    public ResponseEntity<InstructorAdminDto> updateInstructor(
            @Parameter(description = "ID instruktora") @PathVariable UUID id,
            @Valid @RequestBody UpdateInstructorRequest request) {
        InstructorAdminDto instructor = adminInstructorService.updateInstructor(id, request);
        return ResponseEntity.ok(instructor);
    }

    @Operation(
        summary = "Delete instructor",
        description = "Deletes the instructor from the system (including the photo)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Instructor deleted"),
        @ApiResponse(responseCode = "404", description = "Instructor not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInstructor(
            @Parameter(description = "ID instruktora") @PathVariable UUID id) {
        adminInstructorService.deleteInstructor(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Upload instructor photo",
        description = "Adds or replaces the instructor's photo (max 10MB, JPEG/PNG/WebP)"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Photo uploaded"),
        @ApiResponse(responseCode = "400", description = "Invalid file"),
        @ApiResponse(responseCode = "404", description = "Instructor not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PostMapping("/{id}/photo")
    public ResponseEntity<Void> uploadPhoto(
            @Parameter(description = "ID instruktora") @PathVariable UUID id,
            @Parameter(description = "Image file") @RequestParam("file") MultipartFile file) throws IOException {
        adminInstructorService.uploadPhoto(id, file);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Delete instructor photo",
        description = "Deletes the instructor's photo"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Photo deleted"),
        @ApiResponse(responseCode = "404", description = "Instructor not found or no photo"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @DeleteMapping("/{id}/photo")
    public ResponseEntity<Void> deletePhoto(
            @Parameter(description = "ID instruktora") @PathVariable UUID id) throws IOException {
        adminInstructorService.deletePhoto(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Move instructor up")
    @PostMapping("/{id}/move-up")
    public ResponseEntity<List<InstructorAdminDto>> moveUp(
            @Parameter(description = "ID instruktora") @PathVariable UUID id) {
        return ResponseEntity.ok(adminInstructorService.moveUp(id));
    }

    @Operation(summary = "Move instructor down")
    @PostMapping("/{id}/move-down")
    public ResponseEntity<List<InstructorAdminDto>> moveDown(
            @Parameter(description = "ID instruktora") @PathVariable UUID id) {
        return ResponseEntity.ok(adminInstructorService.moveDown(id));
    }

    @Operation(summary = "Duplicate instructor as translation")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Instructor duplicated as translation"),
        @ApiResponse(responseCode = "400", description = "Instructor not found or translation already exists"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PostMapping("/{id}/duplicate-translation")
    public ResponseEntity<InstructorAdminDto> duplicateAsTranslation(
            @Parameter(description = "ID instruktora zrodlowego") @PathVariable UUID id,
            @Valid @RequestBody AdminInstructorDtos.DuplicateAsTranslationRequest request) {
        return ResponseEntity.ok(adminInstructorService.duplicateAsTranslation(id, request.targetLanguage()));
    }

    @Operation(summary = "Sync media to translations", description = "Copies the photo, focal point and badge to the other language versions")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Media synchronized"),
        @ApiResponse(responseCode = "400", description = "Instructor not found"),
        @ApiResponse(responseCode = "403", description = "Admin privileges required")
    })
    @PostMapping("/{id}/sync-media-to-translations")
    public ResponseEntity<AdminInstructorDtos.SyncMediaResultDto> syncMediaToTranslations(
            @Parameter(description = "Source instructor ID") @PathVariable UUID id) {
        return ResponseEntity.ok(adminInstructorService.syncMediaToTranslations(id));
    }

    @Operation(summary = "Set photo from an external URL")
    @PutMapping("/{id}/photo-url")
    public ResponseEntity<InstructorAdminDto> setPhotoUrl(
            @Parameter(description = "ID instruktora") @PathVariable UUID id,
            @RequestBody SetPhotoUrlRequest request) {
        return ResponseEntity.ok(adminInstructorService.setPhotoUrl(id, request.photoUrl()));
    }

    @Operation(summary = "Set instructor badge", description = "Sets or removes the badge on the instructor's photo")
    @PutMapping("/{id}/badge")
    public ResponseEntity<InstructorAdminDto> setBadge(
            @Parameter(description = "ID instruktora") @PathVariable UUID id,
            @RequestBody SetBadgeRequest request) {
        return ResponseEntity.ok(adminInstructorService.setBadge(id, request.badgeUrl()));
    }

    @Operation(summary = "Remove instructor badge")
    @DeleteMapping("/{id}/badge")
    public ResponseEntity<InstructorAdminDto> deleteBadge(
            @Parameter(description = "ID instruktora") @PathVariable UUID id) {
        return ResponseEntity.ok(adminInstructorService.setBadge(id, null));
    }
}
