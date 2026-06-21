package pl.nextsteppro.climbing.api.sitemap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pl.nextsteppro.climbing.domain.course.Course;
import pl.nextsteppro.climbing.domain.course.CourseRepository;
import pl.nextsteppro.climbing.domain.gallery.AlbumRepository;
import pl.nextsteppro.climbing.domain.gallery.AlbumSummaryProjection;
import pl.nextsteppro.climbing.domain.instructor.Instructor;
import pl.nextsteppro.climbing.domain.instructor.InstructorRepository;
import pl.nextsteppro.climbing.domain.instructor.InstructorType;
import pl.nextsteppro.climbing.domain.news.News;
import pl.nextsteppro.climbing.domain.news.NewsRepository;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guards the SEO sitemap against silent regressions: a draft leaking into the
 * index, a path-only / wrong-host URL, malformed XML, or a missing cache header
 * would quietly hurt ranking without ever failing a build.
 *
 * Plain Mockito unit test (same style as FileControllerTest) — Spring Boot 4
 * moved the @WebMvcTest slice into a module this project doesn't pull, and the
 * sitemap body is a plain string we can assert on directly anyway.
 */
@ExtendWith(MockitoExtension.class)
class SitemapControllerTest {

    private static final String BASE = "https://test.nextsteppro.pl";

    @Mock
    private NewsRepository newsRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private AlbumRepository albumRepository;
    @Mock
    private InstructorRepository instructorRepository;

    private SitemapController controller;

    @BeforeEach
    void setUp() {
        controller = new SitemapController(
                newsRepository, courseRepository, albumRepository, instructorRepository, BASE);
    }

    @Test
    void shouldReturnWellFormedXmlWithStaticUrlsAndCacheHeader() {
        // Given
        emptyRepositories();

        // When
        ResponseEntity<byte[]> response = controller.sitemap();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        // Note: content-type (APPLICATION_XML) is applied by the `produces` mapping at the
        // framework level, not on the ResponseEntity, so it isn't visible to a unit test.
        assertTrue(response.getHeaders().getCacheControl().contains("max-age=21600"));

        String xml = body(response);
        assertDoesNotThrow(() -> parseXml(xml), "sitemap must be well-formed XML");
        assertTrue(xml.contains("<loc>" + BASE + "</loc>"));
        assertTrue(xml.contains("<loc>" + BASE + "/calendar</loc>"));
        assertTrue(xml.contains("<loc>" + BASE + "/kursy</loc>"));
        assertTrue(xml.contains("<loc>" + BASE + "/aktualnosci</loc>"));
    }

    @Test
    void shouldIncludePublishedNewsButNotDrafts() {
        // Given
        UUID publishedId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        News published = mock(News.class);
        when(published.isPublished()).thenReturn(true);
        when(published.getId()).thenReturn(publishedId);
        News draft = mock(News.class);
        when(draft.isPublished()).thenReturn(false);
        lenient().when(draft.getId()).thenReturn(draftId); // never rendered; lenient so strict stubs don't flag it

        when(newsRepository.findAll()).thenReturn(List.of(published, draft));
        when(courseRepository.findAll()).thenReturn(List.of());
        when(albumRepository.findAllPublishedAlbumSummaries()).thenReturn(List.of());
        when(instructorRepository.findByActiveTrueOrderByDisplayOrderAscCreatedAtAsc()).thenReturn(List.of());

        // When
        String xml = body(controller.sitemap());

        // Then
        assertTrue(xml.contains(BASE + "/aktualnosci/" + publishedId));
        assertFalse(xml.contains(draftId.toString()), "draft must not leak into sitemap");
    }

    @Test
    void shouldIncludePublishedCoursesButNotDrafts() {
        // Given
        UUID publishedId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        Course published = mock(Course.class);
        when(published.isPublished()).thenReturn(true);
        when(published.getId()).thenReturn(publishedId);
        Course draft = mock(Course.class);
        when(draft.isPublished()).thenReturn(false);
        lenient().when(draft.getId()).thenReturn(draftId);

        when(newsRepository.findAll()).thenReturn(List.of());
        when(courseRepository.findAll()).thenReturn(List.of(published, draft));
        when(albumRepository.findAllPublishedAlbumSummaries()).thenReturn(List.of());
        when(instructorRepository.findByActiveTrueOrderByDisplayOrderAscCreatedAtAsc()).thenReturn(List.of());

        // When
        String xml = body(controller.sitemap());

        // Then
        assertTrue(xml.contains(BASE + "/kursy/" + publishedId));
        assertFalse(xml.contains(draftId.toString()), "draft must not leak into sitemap");
    }

    @Test
    void shouldRouteInstructorsAndCompetitorsToDistinctPaths() {
        // Given
        UUID instructorId = UUID.randomUUID();
        UUID competitorId = UUID.randomUUID();
        Instructor instructor = mock(Instructor.class);
        when(instructor.getMemberType()).thenReturn(InstructorType.INSTRUCTOR);
        when(instructor.getId()).thenReturn(instructorId);
        Instructor competitor = mock(Instructor.class);
        when(competitor.getMemberType()).thenReturn(InstructorType.COMPETITOR);
        when(competitor.getId()).thenReturn(competitorId);

        when(newsRepository.findAll()).thenReturn(List.of());
        when(courseRepository.findAll()).thenReturn(List.of());
        when(albumRepository.findAllPublishedAlbumSummaries()).thenReturn(List.of());
        when(instructorRepository.findByActiveTrueOrderByDisplayOrderAscCreatedAtAsc())
                .thenReturn(List.of(instructor, competitor));

        // When
        String xml = body(controller.sitemap());

        // Then
        assertTrue(xml.contains(BASE + "/team/instruktorzy/" + instructorId));
        assertTrue(xml.contains(BASE + "/team/zawodnicy/" + competitorId));
    }

    @Test
    void shouldIncludePublishedAlbums() {
        // Given
        UUID albumId = UUID.randomUUID();
        AlbumSummaryProjection album = mock(AlbumSummaryProjection.class);
        when(album.getId()).thenReturn(albumId);

        when(newsRepository.findAll()).thenReturn(List.of());
        when(courseRepository.findAll()).thenReturn(List.of());
        when(albumRepository.findAllPublishedAlbumSummaries()).thenReturn(List.of(album));
        when(instructorRepository.findByActiveTrueOrderByDisplayOrderAscCreatedAtAsc()).thenReturn(List.of());

        // When
        String xml = body(controller.sitemap());

        // Then
        assertTrue(xml.contains(BASE + "/galeria/" + albumId));
    }

    @Test
    void shouldPrefixEveryLocWithBaseUrl() {
        // Given
        emptyRepositories();

        // When
        String xml = body(controller.sitemap());

        // Then — no path-only <loc> entries that would resolve against the wrong host
        assertFalse(xml.contains("<loc>/"), "every <loc> must be absolute, prefixed with base-url");
    }

    private void emptyRepositories() {
        when(newsRepository.findAll()).thenReturn(List.of());
        when(courseRepository.findAll()).thenReturn(List.of());
        when(albumRepository.findAllPublishedAlbumSummaries()).thenReturn(List.of());
        when(instructorRepository.findByActiveTrueOrderByDisplayOrderAscCreatedAtAsc()).thenReturn(List.of());
    }

    private static String body(ResponseEntity<byte[]> response) {
        return new String(response.getBody(), StandardCharsets.UTF_8);
    }

    private static void parseXml(String xml) throws Exception {
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
