package pl.nextsteppro.climbing.api.og;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import pl.nextsteppro.climbing.api.course.CourseDtos.CourseDetailDto;
import pl.nextsteppro.climbing.api.course.CourseService;
import pl.nextsteppro.climbing.api.instructor.InstructorDtos.InstructorPublicDto;
import pl.nextsteppro.climbing.api.instructor.InstructorService;
import pl.nextsteppro.climbing.api.news.NewsDtos.NewsDetailDto;
import pl.nextsteppro.climbing.api.news.NewsService;
import pl.nextsteppro.climbing.domain.instructor.InstructorType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Guards the OG stub pages that crawlers/social scrapers receive. Silent
 * breakage here (missing canonical, raw HTML injected from a title, wrong
 * status for a missing id) degrades link previews and SEO without failing a
 * build.
 *
 * Plain Mockito unit test (same style as FileControllerTest) — the stub body is
 * a plain string we can assert on directly.
 */
@ExtendWith(MockitoExtension.class)
class OgControllerTest {

    private static final String BASE = "https://test.nextsteppro.pl";

    @Mock
    private NewsService newsService;
    @Mock
    private CourseService courseService;
    @Mock
    private InstructorService instructorService;

    private OgController controller;

    @BeforeEach
    void setUp() {
        controller = new OgController(newsService, courseService, instructorService, BASE);
    }

    @Test
    void shouldRenderNewsOgWithCoreMetaTags() {
        // Given
        UUID id = UUID.randomUUID();
        when(newsService.getPublishedById(eq(id), any()))
                .thenReturn(newsDto(id, "Wspinaczka dla początkujących", "Krótki opis kursu", BASE + "/files/news/x.jpg"));

        // When
        ResponseEntity<String> response = controller.newsOg(id);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String html = response.getBody();
        assertTrue(html.contains("<meta property=\"og:title\" content=\"Wspinaczka dla początkujących\">"));
        assertTrue(html.contains("<link rel=\"canonical\" href=\"" + BASE + "/aktualnosci/" + id + "\">"));
        assertTrue(html.contains("<meta property=\"og:image\" content=\"" + BASE + "/files/news/x.jpg\">"));
        assertTrue(html.contains("<meta property=\"og:url\" content=\"" + BASE + "/aktualnosci/" + id + "\">"));
        assertTrue(html.contains("<meta http-equiv=\"refresh\" content=\"0;url=" + BASE + "/aktualnosci/" + id + "\">"));
    }

    @Test
    void shouldEscapeHtmlInTitleToPreventInjection() {
        // Given — a malicious / broken title must never reach the rendered HTML raw
        UUID id = UUID.randomUUID();
        when(newsService.getPublishedById(eq(id), any()))
                .thenReturn(newsDto(id, "<script>alert('xss')</script>", "opis", null));

        // When
        String html = controller.newsOg(id).getBody();

        // Then
        assertFalse(html.contains("<script>alert"), "raw script tag must not be rendered");
        assertTrue(html.contains("&lt;script&gt;"));
    }

    @Test
    void shouldFallBackToDefaultImageWhenThumbnailMissing() {
        // Given
        UUID id = UUID.randomUUID();
        when(newsService.getPublishedById(eq(id), any()))
                .thenReturn(newsDto(id, "Tytuł", "opis", null));

        // When
        String html = controller.newsOg(id).getBody();

        // Then
        assertTrue(html.contains("<meta property=\"og:image\" content=\"" + BASE + "/og-default.jpg\">"));
    }

    @Test
    void shouldReturn404WhenNewsMissing() {
        // Given
        UUID id = UUID.randomUUID();
        when(newsService.getPublishedById(eq(id), any())).thenThrow(new RuntimeException("not found"));

        // When
        ResponseEntity<String> response = controller.newsOg(id);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldRenderCourseOgWithCanonical() {
        // Given
        UUID id = UUID.randomUUID();
        when(courseService.getPublishedById(id)).thenReturn(courseDto(id, "Kurs lead", "300 zł"));

        // When
        ResponseEntity<String> response = controller.courseOg(id);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String html = response.getBody();
        assertTrue(html.contains("<meta property=\"og:title\" content=\"Kurs lead\">"));
        assertTrue(html.contains("<link rel=\"canonical\" href=\"" + BASE + "/kursy/" + id + "\">"));
    }

    @Test
    void shouldReturn404WhenCourseMissing() {
        // Given
        UUID id = UUID.randomUUID();
        when(courseService.getPublishedById(id)).thenThrow(new RuntimeException("not found"));

        // When
        ResponseEntity<String> response = controller.courseOg(id);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldRouteCompetitorOgToZawodnicyCanonical() {
        // Given
        UUID id = UUID.randomUUID();
        when(instructorService.getInstructor(id))
                .thenReturn(instructorDto(id, "Jan", "Kowalski", InstructorType.COMPETITOR));

        // When
        String html = controller.instructorOg(id).getBody();

        // Then
        assertTrue(html.contains("<meta property=\"og:title\" content=\"Jan Kowalski — Next Step Pro\">"));
        assertTrue(html.contains("<link rel=\"canonical\" href=\"" + BASE + "/team/zawodnicy/" + id + "\">"));
    }

    @Test
    void shouldRouteInstructorOgToInstruktorzyCanonical() {
        // Given
        UUID id = UUID.randomUUID();
        when(instructorService.getInstructor(id))
                .thenReturn(instructorDto(id, "Anna", "Nowak", InstructorType.INSTRUCTOR));

        // When
        String html = controller.instructorOg(id).getBody();

        // Then
        assertTrue(html.contains("<link rel=\"canonical\" href=\"" + BASE + "/team/instruktorzy/" + id + "\">"));
    }

    @Test
    void shouldReturn404WhenInstructorMissing() {
        // Given
        UUID id = UUID.randomUUID();
        when(instructorService.getInstructor(id)).thenThrow(new RuntimeException("not found"));

        // When
        ResponseEntity<String> response = controller.instructorOg(id);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    private NewsDetailDto newsDto(UUID id, String title, String excerpt, String thumbnailUrl) {
        return new NewsDetailDto(id, title, excerpt, thumbnailUrl, null, null,
                List.of(), Instant.now(), null, "pl", UUID.randomUUID());
    }

    private CourseDetailDto courseDto(UUID id, String title, String price) {
        return new CourseDetailDto(id, title, price, null, null, null,
                "pl", UUID.randomUUID(), List.of(), Instant.now());
    }

    private InstructorPublicDto instructorDto(UUID id, String firstName, String lastName, InstructorType type) {
        return new InstructorPublicDto(id, firstName, lastName, null, null, null,
                "bio", "cert", null, type, null, Instant.now(), "pl", UUID.randomUUID());
    }
}
