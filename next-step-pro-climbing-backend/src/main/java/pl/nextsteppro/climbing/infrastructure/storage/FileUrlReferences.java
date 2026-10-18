package pl.nextsteppro.climbing.infrastructure.storage;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which stored files are in use through a URL rather than through a filename column.
 *
 * <p>⚠️ The gallery and media-library pickers hand back a file's PUBLIC URL, and that URL is what
 * gets saved as a news or course thumbnail, a content-block image, an instructor photo or badge, or
 * a homepage image. Reference counting looked only at the filename columns, so deleting a gallery
 * photo — or running "delete orphaned files" — removed an image that a published article was still
 * showing. Every place that decides whether bytes may go asks here as well.
 *
 * <p>Read straight from the columns that can hold a picked URL (and the settings JSON, which embeds
 * several): a new URL column must be added to {@link #URL_COLUMNS_SQL}, or its images can vanish.
 */
@Component
public class FileUrlReferences {

    private static final Pattern FILE_URL = Pattern.compile("/api/files/([a-z]+)/([A-Za-z0-9._-]+)");

    public static final String URL_COLUMNS_SQL = """
        SELECT thumbnail_url FROM news WHERE thumbnail_url IS NOT NULL
        UNION ALL SELECT image_url FROM news_content_blocks WHERE image_url IS NOT NULL
        UNION ALL SELECT thumbnail_url FROM courses WHERE thumbnail_url IS NOT NULL
        UNION ALL SELECT image_url FROM course_content_blocks WHERE image_url IS NOT NULL
        UNION ALL SELECT photo_external_url FROM instructors WHERE photo_external_url IS NOT NULL
        UNION ALL SELECT badge_url FROM instructors WHERE badge_url IS NOT NULL
        UNION ALL SELECT value FROM site_settings WHERE value LIKE '%/api/files/%'
        """;

    @PersistenceContext
    private EntityManager entityManager;

    /** Filenames in {@code folder} that some URL column points at. */
    @Transactional(readOnly = true)
    public Set<String> referencedIn(String folder) {
        Set<String> found = new HashSet<>();
        for (String text : texts()) {
            Matcher matcher = FILE_URL.matcher(text);
            while (matcher.find()) {
                if (matcher.group(1).equals(folder)) {
                    found.add(matcher.group(2));
                }
            }
        }
        return found;
    }

    @Transactional(readOnly = true)
    public boolean isReferenced(String folder, String filename) {
        return referencedIn(folder).contains(filename);
    }

    @SuppressWarnings("unchecked")
    private List<String> texts() {
        return entityManager.createNativeQuery(URL_COLUMNS_SQL).getResultList();
    }
}
