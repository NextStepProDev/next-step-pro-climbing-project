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
@Tag(name = "Admin - Storage", description = "Audyt plików na dysku")
public class AdminStorageController {

    private final AdminStorageService adminStorageService;

    public AdminStorageController(AdminStorageService adminStorageService) {
        this.adminStorageService = adminStorageService;
    }

    @DeleteMapping("/orphaned")
    @Operation(
            summary = "Usuń osierocone pliki",
            description = "Uruchamia audyt i usuwa wszystkie pliki na dysku, które nie mają rekordu w bazie danych."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liczba usuniętych plików",
                    content = @Content(schema = @Schema(implementation = DeleteOrphanedResult.class))),
            @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora"),
            @ApiResponse(responseCode = "500", description = "Błąd odczytu/zapisu dysku")
    })
    public ResponseEntity<DeleteOrphanedResult> deleteOrphaned() throws IOException {
        int deleted = adminStorageService.deleteOrphanedFiles();
        return ResponseEntity.ok(new DeleteOrphanedResult(deleted));
    }

    @GetMapping("/audit")
    @Operation(
            summary = "Audyt storage",
            description = "Porównuje pliki na dysku z rekordami w bazie danych. " +
                    "Zwraca osierocone pliki (na dysku, brak w DB) i brakujące pliki (w DB, brak na dysku)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Wynik audytu",
                    content = @Content(schema = @Schema(implementation = StorageAuditDto.class))),
            @ApiResponse(responseCode = "403", description = "Brak uprawnień administratora"),
            @ApiResponse(responseCode = "500", description = "Błąd odczytu dysku")
    })
    public ResponseEntity<StorageAuditDto> audit() throws IOException {
        return ResponseEntity.ok(adminStorageService.runAudit());
    }
}
