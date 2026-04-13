package pl.nextsteppro.climbing.api.admin.storage;

import java.util.List;

public class AdminStorageDtos {

    public record StorageAuditDto(
            List<OrphanedFileDto> orphanedFiles,
            List<MissingFileDto> missingFiles,
            int totalFilesOnDisk,
            int totalFilesInDb,
            long totalSizeBytesOnDisk
    ) {}

    public record OrphanedFileDto(
            String folder,
            String filename,
            long sizeBytes
    ) {}

    public record MissingFileDto(
            String folder,
            String filename
    ) {}

    public record DeleteOrphanedResult(int deletedCount) {}
}
