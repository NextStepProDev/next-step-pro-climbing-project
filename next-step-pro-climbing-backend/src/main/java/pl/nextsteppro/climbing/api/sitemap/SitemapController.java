package pl.nextsteppro.climbing.api.sitemap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.nextsteppro.climbing.domain.course.CourseRepository;
import pl.nextsteppro.climbing.domain.gallery.AlbumRepository;
import pl.nextsteppro.climbing.domain.instructor.InstructorRepository;
import pl.nextsteppro.climbing.domain.instructor.InstructorType;
import pl.nextsteppro.climbing.domain.news.NewsRepository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@RestController
public class SitemapController {

    private final NewsRepository newsRepository;
    private final CourseRepository courseRepository;
    private final AlbumRepository albumRepository;
    private final InstructorRepository instructorRepository;
    private final String baseUrl;

    public SitemapController(
            NewsRepository newsRepository,
            CourseRepository courseRepository,
            AlbumRepository albumRepository,
            InstructorRepository instructorRepository,
            @Value("${app.base-url}") String baseUrl) {
        this.newsRepository = newsRepository;
        this.courseRepository = courseRepository;
        this.albumRepository = albumRepository;
        this.instructorRepository = instructorRepository;
        this.baseUrl = baseUrl;
    }

    @GetMapping(value = "/api/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<byte[]> sitemap() {
        var today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        var sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        addUrl(sb, "", "1.0", "weekly", today);
        addUrl(sb, "/calendar", "0.9", "daily", today);
        addUrl(sb, "/kursy", "0.8", "weekly", today);
        addUrl(sb, "/aktualnosci", "0.8", "daily", today);
        addUrl(sb, "/team/instruktorzy", "0.7", "monthly", today);
        addUrl(sb, "/team/zawodnicy", "0.7", "monthly", today);
        addUrl(sb, "/galeria", "0.6", "weekly", today);
        addUrl(sb, "/filmy", "0.6", "weekly", today);
        addUrl(sb, "/kontakt", "0.5", "monthly", today);
        addUrl(sb, "/faq", "0.4", "monthly", today);

        newsRepository.findAll().stream()
                .filter(n -> n.isPublished())
                .forEach(n -> addUrl(sb, "/aktualnosci/" + n.getId(), "0.7", "monthly", today));

        courseRepository.findAll().stream()
                .filter(c -> c.isPublished())
                .forEach(c -> addUrl(sb, "/kursy/" + c.getId(), "0.8", "monthly", today));

        albumRepository.findAllPublishedAlbumSummaries()
                .forEach(a -> addUrl(sb, "/galeria/" + a.getId(), "0.5", "monthly", today));

        var instructors = instructorRepository.findByActiveTrueOrderByDisplayOrderAscCreatedAtAsc();
        instructors.stream()
                .filter(i -> i.getMemberType() == InstructorType.INSTRUCTOR)
                .forEach(i -> addUrl(sb, "/team/instruktorzy/" + i.getId(), "0.6", "monthly", today));
        instructors.stream()
                .filter(i -> i.getMemberType() == InstructorType.COMPETITOR)
                .forEach(i -> addUrl(sb, "/team/zawodnicy/" + i.getId(), "0.6", "monthly", today));

        sb.append("</urlset>");

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(6, TimeUnit.HOURS).cachePublic())
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void addUrl(StringBuilder sb, String path, String priority, String changefreq, String lastmod) {
        sb.append("  <url>\n");
        sb.append("    <loc>").append(baseUrl).append(path).append("</loc>\n");
        sb.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
        sb.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        sb.append("    <priority>").append(priority).append("</priority>\n");
        sb.append("  </url>\n");
    }
}
