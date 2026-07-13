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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/news")
@Tag(name = "News", description = "Public access to news")
public class NewsController {

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @Operation(summary = "Get published news articles")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of news articles")
    })
    @GetMapping
    public ResponseEntity<NewsPageDto> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "pl") String language,
            @RequestParam(required = false) @Nullable String q,
            @RequestParam(defaultValue = "false") boolean starred,
            @Nullable @CurrentUserId UUID userId) {
        return ResponseEntity.ok(newsService.getAllPublished(page, size, language, q, starred, userId));
    }

    @Operation(summary = "Get news article details")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "News article details"),
        @ApiResponse(responseCode = "400", description = "News article not found or unpublished")
    })
    @GetMapping("/{id}")
    public ResponseEntity<NewsDetailDto> getById(
            @Parameter(description = "News article ID") @PathVariable UUID id,
            @Nullable @CurrentUserId UUID userId) {
        return ResponseEntity.ok(newsService.getPublishedById(id, userId));
    }

    @Operation(summary = "Get available language versions of the article")
    @GetMapping("/by-group/{translationGroupId}")
    public ResponseEntity<List<NewsTranslationDto>> getTranslations(@PathVariable UUID translationGroupId) {
        return ResponseEntity.ok(newsService.getAvailableTranslations(translationGroupId));
    }

    @Operation(summary = "Add news article to favorites")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Added to favorites"),
        @ApiResponse(responseCode = "401", description = "Login required")
    })
    @PostMapping("/{id}/star")
    public ResponseEntity<Void> star(
            @Parameter(description = "News article ID") @PathVariable UUID id,
            @Nullable @CurrentUserId UUID userId) {
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        newsService.starNews(id, userId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Remove news article from favorites")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Removed from favorites"),
        @ApiResponse(responseCode = "401", description = "Login required")
    })
    @DeleteMapping("/{id}/star")
    public ResponseEntity<Void> unstar(
            @Parameter(description = "News article ID") @PathVariable UUID id,
            @Nullable @CurrentUserId UUID userId) {
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        newsService.unstarNews(id, userId);
        return ResponseEntity.ok().build();
    }
}
