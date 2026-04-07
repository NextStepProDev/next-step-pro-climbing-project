package pl.nextsteppro.climbing.api.admin.assets;

import java.time.Instant;
import java.util.UUID;

public class AdminAssetsDtos {

    public record AssetDto(
            UUID id,
            String filename,
            String originalName,
            String mimeType,
            long sizeBytes,
            String url,
            Instant createdAt
    ) {}
}
