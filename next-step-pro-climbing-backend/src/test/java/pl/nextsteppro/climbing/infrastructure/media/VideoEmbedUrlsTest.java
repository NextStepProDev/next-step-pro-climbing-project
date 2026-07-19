package pl.nextsteppro.climbing.infrastructure.media;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests for the shared YouTube/Instagram embed-URL normalizer. */
class VideoEmbedUrlsTest {

    @Test
    void shouldNormalizeYoutubeWatchUrl() {
        assertEquals("https://www.youtube.com/embed/dQw4w9WgXcQ",
            VideoEmbedUrls.toEmbedUrlOrNull("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
    }

    @Test
    void shouldNormalizeYoutubeShortLink() {
        assertEquals("https://www.youtube.com/embed/dQw4w9WgXcQ",
            VideoEmbedUrls.toEmbedUrlOrNull("https://youtu.be/dQw4w9WgXcQ"));
    }

    @Test
    void shouldNormalizeYoutubeShorts() {
        assertEquals("https://www.youtube.com/embed/dQw4w9WgXcQ",
            VideoEmbedUrls.toEmbedUrlOrNull("https://www.youtube.com/shorts/dQw4w9WgXcQ"));
    }

    @Test
    void shouldNormalizeInstagramReel() {
        assertEquals("https://www.instagram.com/reel/AbC123/embed/",
            VideoEmbedUrls.toEmbedUrlOrNull("https://www.instagram.com/reel/AbC123/"));
    }

    @Test
    void shouldNormalizeInstagramPost() {
        assertEquals("https://www.instagram.com/p/AbC123/embed/",
            VideoEmbedUrls.toEmbedUrlOrNull("https://www.instagram.com/p/AbC123/"));
    }

    @Test
    void shouldReturnNullForUnsupportedUrl() {
        assertNull(VideoEmbedUrls.toEmbedUrlOrNull("https://docs.google.com/document/d/abc"));
        assertNull(VideoEmbedUrls.toEmbedUrlOrNull("https://vimeo.com/12345"));
        assertNull(VideoEmbedUrls.toEmbedUrlOrNull("not a url"));
    }
}
