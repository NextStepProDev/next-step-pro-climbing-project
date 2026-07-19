package pl.nextsteppro.climbing.infrastructure.media;

import org.jspecify.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes YouTube/Instagram share URLs into their embeddable form so the frontend can
 * drop them straight into an {@code <iframe>}. Shared by News (VIDEO_EMBED blocks) and the
 * training-calendar attachments.
 *
 * <p>{@link #toEmbedUrlOrNull} returns {@code null} for anything that is not a recognised
 * video URL — callers decide whether that is an error (News rejects it) or just "render as a
 * plain link" (attachments).
 */
public final class VideoEmbedUrls {

    private static final Pattern YOUTUBE_WATCH =
        Pattern.compile("(?:youtube\\.com/watch\\?|youtube\\.com/watch\\?.*&)v=([a-zA-Z0-9_-]{11})");
    private static final Pattern YOUTUBE_SHORT = Pattern.compile("youtu\\.be/([a-zA-Z0-9_-]{11})");
    private static final Pattern YOUTUBE_SHORTS = Pattern.compile("youtube\\.com/shorts/([a-zA-Z0-9_-]{11})");
    private static final Pattern INSTAGRAM_REEL = Pattern.compile("instagram\\.com/reel/([a-zA-Z0-9_-]+)");
    private static final Pattern INSTAGRAM_POST = Pattern.compile("instagram\\.com/p/([a-zA-Z0-9_-]+)");

    private VideoEmbedUrls() {}

    /** @return an embeddable URL for supported YouTube/Instagram links, or {@code null} otherwise. */
    @Nullable
    public static String toEmbedUrlOrNull(String inputUrl) {
        String url = inputUrl.trim();

        Matcher m = YOUTUBE_WATCH.matcher(url);
        if (m.find()) return "https://www.youtube.com/embed/" + m.group(1);

        m = YOUTUBE_SHORT.matcher(url);
        if (m.find()) return "https://www.youtube.com/embed/" + m.group(1);

        m = YOUTUBE_SHORTS.matcher(url);
        if (m.find()) return "https://www.youtube.com/embed/" + m.group(1);

        m = INSTAGRAM_REEL.matcher(url);
        if (m.find()) return "https://www.instagram.com/reel/" + m.group(1) + "/embed/";

        m = INSTAGRAM_POST.matcher(url);
        if (m.find()) return "https://www.instagram.com/p/" + m.group(1) + "/embed/";

        return null;
    }
}
