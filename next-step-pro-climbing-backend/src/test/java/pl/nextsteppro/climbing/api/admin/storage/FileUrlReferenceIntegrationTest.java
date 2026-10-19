package pl.nextsteppro.climbing.api.admin.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import pl.nextsteppro.climbing.api.admin.assets.AdminAssetsService;
import pl.nextsteppro.climbing.api.admin.gallery.AdminGalleryService;
import pl.nextsteppro.climbing.domain.assets.SharedAsset;
import pl.nextsteppro.climbing.domain.assets.SharedAssetRepository;
import pl.nextsteppro.climbing.domain.gallery.Album;
import pl.nextsteppro.climbing.domain.gallery.AlbumRepository;
import pl.nextsteppro.climbing.domain.gallery.Photo;
import pl.nextsteppro.climbing.domain.gallery.PhotoRepository;
import pl.nextsteppro.climbing.domain.news.News;
import pl.nextsteppro.climbing.domain.news.NewsRepository;
import pl.nextsteppro.climbing.integration.BaseIntegrationTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Images chosen from the gallery or the media library are saved as URLs on the content that uses
 * them. Before 2026-10-18 nothing that deletes files looked at those URLs.
 */
class FileUrlReferenceIntegrationTest extends BaseIntegrationTest {

    @Value("${app.storage.root}") private String storageRoot;
    @Value("${app.base-url}") private String baseUrl;

    @Autowired private AdminGalleryService galleryService;
    @Autowired private AdminAssetsService assetsService;
    @Autowired private AdminStorageService storageService;
    @Autowired private AlbumRepository albumRepository;
    @Autowired private PhotoRepository photoRepository;
    @Autowired private SharedAssetRepository assetRepository;
    @Autowired private NewsRepository newsRepository;

    /** Upload is not transactional: whatever this class writes to disk it removes itself. */
    private final java.util.List<Path> written = new java.util.ArrayList<>();

    @org.junit.jupiter.api.AfterEach
    void removeFiles() throws Exception {
        for (Path path : written) Files.deleteIfExists(path);
        written.clear();
    }

    @BeforeEach
    void setUp() {
        newsRepository.deleteAll();
        photoRepository.deleteAll();
        albumRepository.deleteAll();
        assetRepository.deleteAll();
    }

    private Path file(String folder, String name) throws Exception {
        Path dir = Path.of(storageRoot, folder);
        Files.createDirectories(dir);
        Path path = dir.resolve(name);
        written.add(path);
        Files.write(path, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0});
        // Older than the orphan sweep's grace window, as anything left from a past upload is
        Files.setLastModifiedTime(path, java.nio.file.attribute.FileTime.from(java.time.Instant.now().minusSeconds(86_400)));
        return path;
    }

    private void articleShowing(String url) {
        News news = new News("Zawody na Jurze");
        news.setThumbnailUrl(url);
        newsRepository.saveAndFlush(news);
    }

    @Test
    void shouldKeepAGalleryPhotoAnArticleStillShows() throws Exception {
        String name = UUID.randomUUID() + ".jpg";
        Path onDisk = file("gallery", name);
        Album album = albumRepository.saveAndFlush(new Album("Jura"));
        Photo photo = photoRepository.saveAndFlush(new Photo(album, name));
        articleShowing(baseUrl + "/api/files/gallery/" + name);

        galleryService.deletePhoto(photo.getId());

        assertTrue(Files.exists(onDisk), "the article's thumbnail must not disappear with the gallery entry");
        assertFalse(storageService.runAudit().orphanedFiles().stream().anyMatch(o -> o.filename().equals(name)),
            "a file an article points at is not an orphan");
    }

    @Test
    void shouldKeepAMediaLibraryFileTheHomepageShows() throws Exception {
        String name = UUID.randomUUID() + ".jpg";
        Path onDisk = file("assets", name);
        SharedAsset asset = assetRepository.saveAndFlush(new SharedAsset(name, "baner.jpg", "image/jpeg", 4L));
        articleShowing(baseUrl + "/api/files/assets/" + name);

        assetsService.delete(asset.getId());

        assertTrue(Files.exists(onDisk));
    }

    @Test
    void shouldStillDeleteAPhotoNobodyUses() throws Exception {
        String name = UUID.randomUUID() + ".jpg";
        Path onDisk = file("gallery", name);
        Album album = albumRepository.saveAndFlush(new Album("Jura"));
        Photo photo = photoRepository.saveAndFlush(new Photo(album, name));

        galleryService.deletePhoto(photo.getId());
        // Not committed yet, so the bytes must still be there: had the transaction rolled back
        // here, the photo row would survive and point at a file that no longer existed.
        assertTrue(Files.exists(onDisk), "Nothing may be unlinked before the commit");
        unlinkDeletedFiles(); // storage unlinks on COMMIT, and this test rolls back

        assertFalse(Files.exists(onDisk));
    }

    @Test
    void shouldNotCallAFreshUploadAnOrphanBeforeItsRowIsSaved() throws Exception {
        // The bytes are written before the row commits; a sweep in between used to delete them.
        String name = UUID.randomUUID() + ".jpg";
        Path dir = Path.of(storageRoot, "news");
        Files.createDirectories(dir);
        written.add(dir.resolve(name));
        Files.write(dir.resolve(name), new byte[]{1});

        assertFalse(storageService.runAudit().orphanedFiles().stream().anyMatch(o -> o.filename().equals(name)));
    }

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void shouldReadEveryUrlColumnTheSchemaHas() {
        // A new column that stores a picked image's URL and is missing from FileUrlReferences brings
        // the vanishing images back: deleting the gallery photo, or one orphan sweep, takes them.
        java.util.Set<String> notPickedImages = java.util.Set.of(
            "videos.youtube_url",          // a YouTube link, never one of our files
            "instructors.profile_8a_url"   // a profile on 8a.nu, never one of our files
        );
        java.util.List<String> columns = jdbc.queryForList("""
            SELECT table_name || '.' || column_name FROM information_schema.columns
            WHERE table_schema = 'public' AND column_name LIKE '%\\_url' ESCAPE '\\'
            """, String.class);
        assertTrue(columns.size() >= 7, "the scan must see the URL columns it guards: " + columns);

        String sql = pl.nextsteppro.climbing.infrastructure.storage.FileUrlReferences.URL_COLUMNS_SQL;
        java.util.List<String> unread = columns.stream()
            .filter(c -> !notPickedImages.contains(c))
            .filter(c -> {
                String[] parts = c.split("\\.");
                return !java.util.regex.Pattern.compile("SELECT\\s+" + parts[1] + "\\s+FROM\\s+" + parts[0] + "\\b")
                    .matcher(sql).find();
            })
            .toList();
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(), unread);
    }
}
