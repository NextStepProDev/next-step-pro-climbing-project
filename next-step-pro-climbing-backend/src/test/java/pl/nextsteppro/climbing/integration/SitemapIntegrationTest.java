package pl.nextsteppro.climbing.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import pl.nextsteppro.climbing.api.sitemap.SitemapController;
import pl.nextsteppro.climbing.domain.course.CourseRepository;
import pl.nextsteppro.climbing.domain.gallery.Album;
import pl.nextsteppro.climbing.domain.gallery.AlbumRepository;
import pl.nextsteppro.climbing.domain.instructor.Instructor;
import pl.nextsteppro.climbing.domain.instructor.InstructorRepository;
import pl.nextsteppro.climbing.domain.instructor.InstructorType;
import pl.nextsteppro.climbing.domain.news.NewsRepository;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Closes the gap the Mockito SitemapControllerTest structurally cannot reach.
 *
 * News/courses are filtered in Java (findAll().filter(isPublished())), so the
 * unit test already guards them. But albums and instructors are filtered at the
 * SQL level — `findAllPublishedAlbumSummaries()` (WHERE is_published = true) and
 * `findByActiveTrueOrderByDisplayOrderAscCreatedAtAsc()` (WHERE is_active =
 * true). A mock stubs those queries, so it can never catch one of them being
 * broken and leaking an unpublished album or inactive instructor into the
 * sitemap. This test runs the real queries against real Postgres through the
 * real controller.
 *
 * No MockMvc: @AutoConfigureMockMvc isn't on the classpath in this Spring Boot
 * version, and the controller body is the only thing past the query, so we drive
 * it directly with autowired repositories.
 */
class SitemapIntegrationTest extends BaseIntegrationTest {

    private static final String BASE = "https://test.nextsteppro.pl";

    @Autowired
    private NewsRepository newsRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private AlbumRepository albumRepository;
    @Autowired
    private InstructorRepository instructorRepository;

    @Test
    void shouldExcludeUnpublishedAlbumFromSitemap() {
        // Given — one published, one draft album, persisted to the real DB
        Album published = new Album("Published Album");
        published.setPublished(true);
        Album draft = new Album("Draft Album");
        draft.setPublished(false);
        UUID publishedId = albumRepository.saveAndFlush(published).getId();
        UUID draftId = albumRepository.saveAndFlush(draft).getId();

        // When — the real findAllPublishedAlbumSummaries() query runs
        String xml = renderSitemap();

        // Then — only the published album reaches a <loc>
        assertTrue(xml.contains(BASE + "/galeria/" + publishedId),
                "published album must be in the sitemap");
        assertFalse(xml.contains(draftId.toString()),
                "draft album must NOT leak into the sitemap");
    }

    @Test
    void shouldExcludeInactiveInstructorFromSitemap() {
        // Given — active + inactive instructor, and an active competitor
        Instructor active = newInstructor("Anna", "Active", InstructorType.INSTRUCTOR, true);
        Instructor inactive = newInstructor("Igor", "Inactive", InstructorType.INSTRUCTOR, false);
        Instructor competitor = newInstructor("Zofia", "Competitor", InstructorType.COMPETITOR, true);
        UUID activeId = instructorRepository.saveAndFlush(active).getId();
        UUID inactiveId = instructorRepository.saveAndFlush(inactive).getId();
        UUID competitorId = instructorRepository.saveAndFlush(competitor).getId();

        // When — the real findByActiveTrueOrderByDisplayOrderAscCreatedAtAsc() query runs
        String xml = renderSitemap();

        // Then — only active members appear, on their type-specific paths
        assertTrue(xml.contains(BASE + "/team/instruktorzy/" + activeId),
                "active instructor must be in the sitemap");
        assertTrue(xml.contains(BASE + "/team/zawodnicy/" + competitorId),
                "active competitor must be in the sitemap on the zawodnicy path");
        assertFalse(xml.contains(inactiveId.toString()),
                "inactive instructor must NOT leak into the sitemap");
    }

    private Instructor newInstructor(String first, String last, InstructorType type, boolean active) {
        Instructor i = new Instructor(first, last);
        i.setMemberType(type);
        i.setActive(active);
        return i;
    }

    /** Drives the real SitemapController with the autowired (real) repositories. */
    private String renderSitemap() {
        SitemapController controller = new SitemapController(
                newsRepository, courseRepository, albumRepository, instructorRepository, BASE);
        ResponseEntity<byte[]> response = controller.sitemap();
        return new String(response.getBody(), StandardCharsets.UTF_8);
    }
}
