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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import pl.nextsteppro.climbing.api.calendar.CalendarService;
import pl.nextsteppro.climbing.api.calendar.EventOgView;
import pl.nextsteppro.climbing.api.course.CourseDtos.CourseDetailDto;
import pl.nextsteppro.climbing.api.course.CourseService;
import pl.nextsteppro.climbing.api.instructor.InstructorDtos.InstructorPublicDto;
import pl.nextsteppro.climbing.api.instructor.InstructorService;
import pl.nextsteppro.climbing.api.news.NewsDtos.NewsDetailDto;
import pl.nextsteppro.climbing.api.news.NewsService;
import pl.nextsteppro.climbing.domain.instructor.InstructorType;

@RestController
@RequestMapping("/api/og")
public class OgController {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.of("pl"));

    private final NewsService newsService;
    private final CourseService courseService;
    private final InstructorService instructorService;
    private final CalendarService calendarService;
    private final String baseUrl;

    public OgController(NewsService newsService,
                        CourseService courseService,
                        InstructorService instructorService,
                        CalendarService calendarService,
                        @Value("${app.base-url}") String baseUrl) {
        this.newsService = newsService;
        this.courseService = courseService;
        this.instructorService = instructorService;
        this.calendarService = calendarService;
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
                : baseUrl + "/og-image.jpg";

        return ogResponse(buildHtml("article", title, description, image, pageUrl));
    }

    @GetMapping(value = "/course/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> courseOg(@PathVariable UUID id) {
        CourseDetailDto course;
        try {
            course = courseService.getPublishedById(id);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }

        String pageUrl = baseUrl + "/kursy/" + id;
        String title = escapeHtml(course.title());
        String description = course.price() != null
                ? escapeHtml("Kurs wspinaczkowy — " + course.price())
                : "Next Step Pro Climbing";
        String image = course.thumbnailUrl() != null
                ? course.thumbnailUrl()
                : baseUrl + "/og-image.jpg";

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
                : baseUrl + "/og-image.jpg";

        return ogResponse(buildHtml("profile", title, description, image, pageUrl));
    }

    @GetMapping(value = "/event/{id}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> eventOg(@PathVariable UUID id) {
        EventOgView event;
        try {
            event = calendarService.getEventOgView(id);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }

        String pageUrl = baseUrl + "/events/" + id;
        String title = escapeHtml(event.title());
        String description = buildEventDescription(event);
        // Events have no own image; reuse the linked course thumbnail when the
        // course is published, otherwise the site default.
        String image = baseUrl + "/og-image.jpg";
        if (event.courseId() != null && event.coursePublished()) {
            try {
                CourseDetailDto course = courseService.getPublishedById(event.courseId());
                if (course.thumbnailUrl() != null) {
                    image = course.thumbnailUrl();
                }
            } catch (Exception ignored) {
                // course unavailable -> keep default image
            }
        }

        return ogResponse(buildHtml("article", title, description, image, pageUrl));
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

    private String buildEventDescription(EventOgView event) {
        LocalDate start = event.startDate();
        LocalDate end = event.endDate();
        String dateStr = start.equals(end)
                ? DATE_FMT.format(start)
                : DATE_FMT.format(start) + " – " + DATE_FMT.format(end);
        if (event.location() != null && !event.location().isBlank()) {
            return escapeHtml(truncate(dateStr + " · " + event.location(), 200));
        }
        return escapeHtml(dateStr);
    }

    private String buildHtml(String type, String title, String description, String image, String pageUrl) {
        return """
                <!DOCTYPE html>
                <html lang="pl">
                <head>
                  <meta charset="UTF-8">
                  <title>%s</title>
                  <meta name="description" content="%s">
                  <link rel="canonical" href="%s">
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
                """.formatted(title, description, pageUrl, type, title, description, image, pageUrl,
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
