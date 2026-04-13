package pl.nextsteppro.climbing.api.admin.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.nextsteppro.climbing.api.admin.storage.AdminStorageDtos.MissingFileDto;
import pl.nextsteppro.climbing.api.admin.storage.AdminStorageDtos.OrphanedFileDto;
import pl.nextsteppro.climbing.api.admin.storage.AdminStorageDtos.StorageAuditDto;
import pl.nextsteppro.climbing.domain.assets.SharedAssetRepository;
import pl.nextsteppro.climbing.domain.course.CourseContentBlockRepository;
import pl.nextsteppro.climbing.domain.course.CourseRepository;
import pl.nextsteppro.climbing.domain.gallery.PhotoRepository;
import pl.nextsteppro.climbing.domain.instructor.InstructorRepository;
import pl.nextsteppro.climbing.domain.news.NewsContentBlockRepository;
import pl.nextsteppro.climbing.domain.news.NewsRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AdminStorageService {

    private static final Logger logger = LoggerFactory.getLogger(AdminStorageService.class);

    private static final Map<String, String> FOLDER_LABELS = Map.of(
            "gallery", "Galeria",
            "news", "Aktualności",
            "courses", "Kursy",
            "instructors", "Instruktorzy",
            "assets", "Biblioteka mediów"
    );

    private final Path rootPath;
    private final PhotoRepository photoRepository;
    private final NewsRepository newsRepository;
    private final NewsContentBlockRepository newsBlockRepository;
    private final CourseRepository courseRepository;
    private final CourseContentBlockRepository courseBlockRepository;
    private final InstructorRepository instructorRepository;
    private final SharedAssetRepository sharedAssetRepository;

    public AdminStorageService(
            @Value("${app.storage.root:/app/uploads}") String rootPath,
            PhotoRepository photoRepository,
            NewsRepository newsRepository,
            NewsContentBlockRepository newsBlockRepository,
            CourseRepository courseRepository,
            CourseContentBlockRepository courseBlockRepository,
            InstructorRepository instructorRepository,
            SharedAssetRepository sharedAssetRepository) {
        this.rootPath = Paths.get(rootPath);
        this.photoRepository = photoRepository;
        this.newsRepository = newsRepository;
        this.newsBlockRepository = newsBlockRepository;
        this.courseRepository = courseRepository;
        this.courseBlockRepository = courseBlockRepository;
        this.instructorRepository = instructorRepository;
        this.sharedAssetRepository = sharedAssetRepository;
    }

    public StorageAuditDto runAudit() throws IOException {
        // Step 1: Build DB index — folder → set of known filenames
        Map<String, Set<String>> dbFiles = buildDbIndex();

        // Step 2: Scan disk — folder → set of actual filenames
        Map<String, Set<String>> diskFiles = scanDisk();

        List<OrphanedFileDto> orphaned = new ArrayList<>();
        List<MissingFileDto> missing = new ArrayList<>();
        int totalOnDisk = 0;
        long totalSizeBytes = 0;

        // Step 3: Find orphaned files (on disk, not in DB)
        for (Map.Entry<String, Set<String>> entry : diskFiles.entrySet()) {
            String folder = entry.getKey();
            Set<String> known = dbFiles.getOrDefault(folder, Set.of());

            for (String filename : entry.getValue()) {
                totalOnDisk++;
                Path filePath = rootPath.resolve(folder).resolve(filename);
                long size = 0;
                try {
                    size = Files.size(filePath);
                } catch (IOException e) {
                    logger.warn("Could not read size for: {}/{}", folder, filename);
                }
                totalSizeBytes += size;

                if (!known.contains(filename)) {
                    orphaned.add(new OrphanedFileDto(folder, filename, size));
                }
            }
        }

        // Step 4: Find missing files (in DB, not on disk)
        for (Map.Entry<String, Set<String>> entry : dbFiles.entrySet()) {
            String folder = entry.getKey();
            Set<String> onDisk = diskFiles.getOrDefault(folder, Set.of());

            for (String filename : entry.getValue()) {
                if (!onDisk.contains(filename)) {
                    missing.add(new MissingFileDto(folder, filename));
                }
            }
        }

        int totalInDb = dbFiles.values().stream().mapToInt(Set::size).sum();

        logger.info("Storage audit complete: {} files on disk, {} in DB, {} orphaned, {} missing",
                totalOnDisk, totalInDb, orphaned.size(), missing.size());

        return new StorageAuditDto(orphaned, missing, totalOnDisk, totalInDb, totalSizeBytes);
    }

    public int deleteOrphanedFiles() throws IOException {
        StorageAuditDto audit = runAudit();
        int deleted = 0;

        for (OrphanedFileDto orphan : audit.orphanedFiles()) {
            Path filePath = rootPath.resolve(orphan.folder()).resolve(orphan.filename());
            try {
                Files.deleteIfExists(filePath);
                deleted++;
                logger.info("Deleted orphaned file: {}/{}", orphan.folder(), orphan.filename());
            } catch (IOException e) {
                logger.warn("Failed to delete orphaned file: {}/{} - {}", orphan.folder(), orphan.filename(), e.getMessage());
            }
        }

        logger.info("Orphaned files cleanup complete: {}/{} deleted", deleted, audit.orphanedFiles().size());
        return deleted;
    }

    private Map<String, Set<String>> buildDbIndex() {
        Map<String, Set<String>> index = new HashMap<>();

        index.put("gallery", new HashSet<>(photoRepository.findAllFilenames()));

        Set<String> newsFiles = new HashSet<>(newsRepository.findAllThumbnailFilenames());
        newsFiles.addAll(newsBlockRepository.findAllImageFilenames());
        index.put("news", newsFiles);

        Set<String> courseFiles = new HashSet<>(courseRepository.findAllThumbnailFilenames());
        courseFiles.addAll(courseBlockRepository.findAllImageFilenames());
        index.put("courses", courseFiles);

        index.put("instructors", new HashSet<>(instructorRepository.findAllPhotoFilenames()));
        index.put("assets", new HashSet<>(sharedAssetRepository.findAllFilenames()));

        return index;
    }

    private Map<String, Set<String>> scanDisk() throws IOException {
        Map<String, Set<String>> result = new HashMap<>();

        for (String folder : FOLDER_LABELS.keySet()) {
            Path folderPath = rootPath.resolve(folder);
            Set<String> files = new HashSet<>();

            if (Files.isDirectory(folderPath)) {
                try (var stream = Files.list(folderPath)) {
                    stream
                            .filter(Files::isRegularFile)
                            .map(p -> p.getFileName().toString())
                            .forEach(files::add);
                }
            }

            result.put(folder, files);
        }

        return result;
    }
}
