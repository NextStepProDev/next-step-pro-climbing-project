package pl.nextsteppro.climbing.api.og;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import pl.nextsteppro.climbing.api.instructor.InstructorDtos.InstructorPublicDto;
import pl.nextsteppro.climbing.api.instructor.InstructorService;
import pl.nextsteppro.climbing.api.news.NewsDtos.NewsDetailDto;
import pl.nextsteppro.climbing.api.news.NewsService;
import pl.nextsteppro.climbing.domain.instructor.InstructorType;

import java.util.UUID;

@RestController
@RequestMapping("/api/og")
public class OgController {

    private final NewsService newsService;
    private final InstructorService instructorService;
    private final String baseUrl;

    public OgController(NewsService newsService,
                        InstructorService instructorService,
                        @Value("${app.base-url}") String baseUrl) {
        this.newsService = newsService;
        this.instructorService = instructorService;
        this.baseUrl = baseUrl;
    }

    @GetMapping(value = "/news/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> newsOg(@PathVariable UUID id) {
        NewsDetailDto article;
        try {
            article = newsService.getPublishedById(id, null);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }

        String pageUrl = baseUrl + "/aktualnosci/" + id;
        String title = escapeHtml(article.title());
        String description = article.excerpt() != null
                ? escapeHtml(truncate(stripHtml(article.excerpt()), 200))
                : "Next Step Pro Climbing";
        String image = article.thumbnailUrl() != null
                ? article.thumbnailUrl()
                : baseUrl + "/og-default.jpg";

        return ogResponse(buildHtml("article", title, description, image, pageUrl));
    }

    @GetMapping(value = "/instructor/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> instructorOg(@PathVariable UUID id) {
        InstructorPublicDto instructor;
        try {
            instructor = instructorService.getInstructor(id);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }

        String slug = instructor.memberType() == InstructorType.COMPETITOR
                ? "zawodnicy" : "instruktorzy";
        String pageUrl = baseUrl + "/team/" + slug + "/" + id;
        String fullName = instructor.firstName() + " " + instructor.lastName();
        String title = escapeHtml(fullName + " — Next Step Pro");
        String description = buildInstructorDescription(instructor);
        String image = instructor.photoUrl() != null
                ? instructor.photoUrl()
                : baseUrl + "/og-default.jpg";

        return ogResponse(buildHtml("profile", title, description, image, pageUrl));
    }

    private ResponseEntity<String> ogResponse(String body) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(body);
    }

    private String buildInstructorDescription(InstructorPublicDto instructor) {
        if (instructor.certifications() != null && !instructor.certifications().isBlank()) {
            String first = instructor.certifications().split("\n")[0].trim();
            return escapeHtml(truncate(first, 200));
        }
        if (instructor.bio() != null && !instructor.bio().isBlank()) {
            return escapeHtml(truncate(stripHtml(instructor.bio()), 200));
        }
        return "Next Step Pro Climbing";
    }

    private String buildHtml(String type, String title, String description, String image, String pageUrl) {
        return """
                <!DOCTYPE html>
                <html lang="pl">
                <head>
                  <meta charset="UTF-8">
                  <title>%s</title>
                  <meta property="og:type" content="%s">
                  <meta property="og:site_name" content="Next Step Pro">
                  <meta property="og:title" content="%s">
                  <meta property="og:description" content="%s">
                  <meta property="og:image" content="%s">
                  <meta property="og:url" content="%s">
                  <meta property="og:locale" content="pl_PL">
                  <meta name="twitter:card" content="summary_large_image">
                  <meta name="twitter:title" content="%s">
                  <meta name="twitter:description" content="%s">
                  <meta name="twitter:image" content="%s">
                  <meta http-equiv="refresh" content="0;url=%s">
                </head>
                <body></body>
                </html>
                """.formatted(title, type, title, description, image, pageUrl,
                              title, description, image, pageUrl);
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String stripHtml(String s) {
        return s.replaceAll("<[^>]*>", "").trim();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
