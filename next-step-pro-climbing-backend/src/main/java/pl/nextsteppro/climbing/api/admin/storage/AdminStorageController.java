package pl.nextsteppro.climbing.api.admin.storage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.nextsteppro.climbing.api.admin.storage.AdminStorageDtos.DeleteOrphanedResult;
import pl.nextsteppro.climbing.api.admin.storage.AdminStorageDtos.StorageAuditDto;

import java.io.IOException;

@RestController
@RequestMapping("/api/admin/storage")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Storage", description = "On-disk file audit")
public class AdminStorageController {

    private final AdminStorageService adminStorageService;

    public AdminStorageController(AdminStorageService adminStorageService) {
        this.adminStorageService = adminStorageService;
    }

    @DeleteMapping("/orphaned")
    @Operation(
            summary = "Delete orphaned files",
            description = "Runs an audit and deletes all files on disk that have no database record."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Number of deleted files",
                    content = @Content(schema = @Schema(implementation = DeleteOrphanedResult.class))),
            @ApiResponse(responseCode = "403", description = "Admin privileges required"),
            @ApiResponse(responseCode = "500", description = "Disk read/write error")
    })
    public ResponseEntity<DeleteOrphanedResult> deleteOrphaned() throws IOException {
        int deleted = adminStorageService.deleteOrphanedFiles();
        return ResponseEntity.ok(new DeleteOrphanedResult(deleted));
    }

    @GetMapping("/audit")
    @Operation(
            summary = "Storage audit",
            description = "Compares files on disk with database records. " +
                    "Returns orphaned files (on disk, missing in DB) and missing files (in DB, missing on disk)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Audit result",
                    content = @Content(schema = @Schema(implementation = StorageAuditDto.class))),
            @ApiResponse(responseCode = "403", description = "Admin privileges required"),
            @ApiResponse(responseCode = "500", description = "Disk read error")
    })
    public ResponseEntity<StorageAuditDto> audit() throws IOException {
        return ResponseEntity.ok(adminStorageService.runAudit());
    }
}
