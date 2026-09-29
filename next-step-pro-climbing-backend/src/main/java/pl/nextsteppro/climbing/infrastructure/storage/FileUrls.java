package pl.nextsteppro.climbing.infrastructure.storage;

import org.jspecify.annotations.Nullable;

/**
 * URLs of files served by the public {@code /api/files/{folder}/{filename}} routes. Absolute,
 * because they travel to places that do not share the site's origin (mail, OG tags, the CMS
 * editor preview), with one exception: the avatar, whose only consumer is the SPA itself.
 */
public final class FileUrls {

    private FileUrls() {}

    public static String of(String baseUrl, String folder, String filename) {
        return baseUrl + "/api/files/" + folder + "/" + filename;
    }

    /**
     * An externally hosted image wins over an uploaded one. That is how every CMS image column
     * pair ({@code *_url} + {@code *_filename}) is read, and {@code null} when there is neither.
     */
    public static @Nullable String preferExternal(@Nullable String externalUrl, String baseUrl,
                                                  String folder, @Nullable String filename) {
        if (externalUrl != null) return externalUrl;
        return filename != null ? of(baseUrl, folder, filename) : null;
    }

    /** Site-relative on purpose: only the SPA shows avatars, and it already sits on the origin. */
    public static @Nullable String avatar(@Nullable String filename) {
        return filename != null ? "/api/files/avatars/" + filename : null;
    }
}
