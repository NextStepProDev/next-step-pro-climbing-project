package pl.nextsteppro.climbing.api.news;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.nextsteppro.climbing.api.news.NewsDtos.*;
import pl.nextsteppro.climbing.config.CurrentUserId;

import java.util.UUID;

@RestController
@RequestMapping("/api/news")
@Tag(name = "News", description = "Publiczny dostęp do aktualności")
public class NewsController {

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @Operation(summary = "Pobierz listę opublikowanych aktualności")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista aktualności")
    })
    @GetMapping
    public ResponseEntity<NewsPageDto> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) @Nullable String q,
            @RequestParam(defaultValue = "false") boolean starred,
            @Nullable @CurrentUserId UUID userId) {
        return ResponseEntity.ok(newsService.getAllPublished(page, size, q, starred, userId));
    }

    @Operation(summary = "Pobierz szczegóły aktualności")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Szczegóły aktualności"),
        @ApiResponse(responseCode = "400", description = "Aktualność nie znaleziona lub nieopublikowana")
    })
    @GetMapping("/{id}")
    public ResponseEntity<NewsDetailDto> getById(
            @Parameter(description = "ID aktualności") @PathVariable UUID id,
            @Nullable @CurrentUserId UUID userId) {
        return ResponseEntity.ok(newsService.getPublishedById(id, userId));
    }

    @Operation(summary = "Dodaj aktualność do ulubionych")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dodano do ulubionych"),
        @ApiResponse(responseCode = "401", description = "Wymagane logowanie")
    })
    @PostMapping("/{id}/star")
    public ResponseEntity<Void> star(
            @Parameter(description = "ID aktualności") @PathVariable UUID id,
            @Nullable @CurrentUserId UUID userId) {
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        newsService.starNews(id, userId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Usuń aktualność z ulubionych")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usunięto z ulubionych"),
        @ApiResponse(responseCode = "401", description = "Wymagane logowanie")
    })
    @DeleteMapping("/{id}/star")
    public ResponseEntity<Void> unstar(
            @Parameter(description = "ID aktualności") @PathVariable UUID id,
            @Nullable @CurrentUserId UUID userId) {
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        newsService.unstarNews(id, userId);
        return ResponseEntity.ok().build();
    }
}
