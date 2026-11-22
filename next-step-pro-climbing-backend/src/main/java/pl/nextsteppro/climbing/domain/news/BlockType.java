package pl.nextsteppro.climbing.domain.news;

public enum BlockType {
    TEXT,
    IMAGE,
    VIDEO_EMBED,
    /** Points at an existing gallery album ({@code album_id}); holds no photos of its own. */
    GALLERY
}
