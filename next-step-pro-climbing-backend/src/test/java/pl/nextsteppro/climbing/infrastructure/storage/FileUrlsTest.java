package pl.nextsteppro.climbing.infrastructure.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FileUrlsTest {

    private static final String BASE = "https://nextsteppro.pl";

    @Test
    void shouldPointAtThePublicFilesRoute() {
        assertEquals(BASE + "/api/files/news/a.jpg", FileUrls.of(BASE, "news", "a.jpg"));
    }

    @Test
    void shouldPreferAnExternalImageOverAnUploadedOne() {
        assertEquals("https://cdn.example/x.jpg", FileUrls.preferExternal("https://cdn.example/x.jpg", BASE, "news", "a.jpg"));
        assertEquals(BASE + "/api/files/courses/a.jpg", FileUrls.preferExternal(null, BASE, "courses", "a.jpg"));
        assertNull(FileUrls.preferExternal(null, BASE, "courses", null));
    }

    @Test
    void shouldKeepTheAvatarSiteRelative() {
        assertEquals("/api/files/avatars/me.jpg", FileUrls.avatar("me.jpg"));
        assertNull(FileUrls.avatar(null));
    }
}
