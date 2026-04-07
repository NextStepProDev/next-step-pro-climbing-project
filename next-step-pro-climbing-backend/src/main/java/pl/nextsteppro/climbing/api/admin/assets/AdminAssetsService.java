package pl.nextsteppro.climbing.api.admin.assets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.api.admin.assets.AdminAssetsDtos.AssetDto;
import pl.nextsteppro.climbing.domain.assets.SharedAsset;
import pl.nextsteppro.climbing.domain.assets.SharedAssetRepository;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AdminAssetsService {

    private static final Logger logger = LoggerFactory.getLogger(AdminAssetsService.class);
    private static final String FOLDER = "assets";

    private final SharedAssetRepository sharedAssetRepository;
    private final FileStorageService fileStorageService;
    private final String baseUrl;

    public AdminAssetsService(SharedAssetRepository sharedAssetRepository,
                              FileStorageService fileStorageService,
                              @Value("${app.base-url}") String baseUrl) {
        this.sharedAssetRepository = sharedAssetRepository;
        this.fileStorageService = fileStorageService;
        this.baseUrl = baseUrl;
    }

    public List<AssetDto> list() {
        return sharedAssetRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toDto)
                .toList();
    }

    public AssetDto upload(MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String mimeType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        long sizeBytes = file.getSize();

        String filename = fileStorageService.store(file, FOLDER);
        SharedAsset asset = new SharedAsset(filename, originalName, mimeType, sizeBytes);
        sharedAssetRepository.save(asset);

        return toDto(asset);
    }

    public void delete(UUID id) throws IOException {
        SharedAsset asset = sharedAssetRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found"));

        try {
            fileStorageService.delete(asset.getFilename(), FOLDER);
        } catch (IOException e) {
            logger.warn("Failed to delete asset file: {}", e.getMessage());
        }

        sharedAssetRepository.delete(asset);
    }

    private AssetDto toDto(SharedAsset asset) {
        return new AssetDto(
                asset.getId(),
                asset.getFilename(),
                asset.getOriginalName(),
                asset.getMimeType(),
                asset.getSizeBytes(),
                baseUrl + "/api/files/" + FOLDER + "/" + asset.getFilename(),
                asset.getCreatedAt()
        );
    }
}
