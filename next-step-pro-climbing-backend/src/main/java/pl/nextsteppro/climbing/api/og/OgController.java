package pl.nextsteppro.climbing.api.og;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.nextsteppro.climbing.api.news.NewsService;
import pl.nextsteppro.climbing.api.news.NewsDtos.NewsDetailDto;

import java.util.UUID;

@RestController
@RequestMapping("/api/og")
public class OgController {

    private final NewsService newsService;
    private final String baseUrl;

    public OgController(NewsService newsService,
                        @Value("${app.base-url}") String baseUrl) {
        this.newsService = newsService;
        this.baseUrl = baseUrl;
    }

    @GetMapping(value = "/news/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> newsOg(@PathVariable UUID id) {
        NewsDetailDto article;
        try {
            article = newsService.getPublishedById(id, null);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }

        String pageUrl = baseUrl + "/aktualnosci/" + id;
        String title = escapeHtml(article.title());
        String description = article.excerpt() != null
                ? escapeHtml(stripHtml(article.excerpt()))
                : "Next Step Pro Climbing";
        String image = article.thumbnailUrl() != null
                ? article.thumbnailUrl()
                : baseUrl + "/og-default.jpg";

        String html = """
                <!DOCTYPE html>
                <html lang="pl">
                <head>
                  <meta charset="UTF-8">
                  <title>%s</title>
                  <meta property="og:type" content="article">
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
                """.formatted(title, title, description, image, pageUrl,
                              title, description, image, pageUrl);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
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
}
