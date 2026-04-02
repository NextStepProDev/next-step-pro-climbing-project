package pl.nextsteppro.climbing.api.news;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.nextsteppro.climbing.api.news.NewsDtos.*;

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
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(newsService.getAllPublished(page, size));
    }

    @Operation(summary = "Pobierz szczegóły aktualności")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Szczegóły aktualności"),
        @ApiResponse(responseCode = "400", description = "Aktualność nie znaleziona lub nieopublikowana")
    })
    @GetMapping("/{id}")
    public ResponseEntity<NewsDetailDto> getById(
            @Parameter(description = "ID aktualności") @PathVariable UUID id) {
        return ResponseEntity.ok(newsService.getPublishedById(id));
    }
}
