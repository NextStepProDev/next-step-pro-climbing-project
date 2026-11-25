package pl.nextsteppro.climbing.api.contact;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import pl.nextsteppro.climbing.domain.contact.ContactMessage;
import pl.nextsteppro.climbing.domain.contact.ContactMessageFile;
import pl.nextsteppro.climbing.domain.contact.ContactMessageFileRepository;
import pl.nextsteppro.climbing.domain.contact.ContactMessageRepository;
import pl.nextsteppro.climbing.infrastructure.i18n.LocalizedMessage;
import pl.nextsteppro.climbing.infrastructure.storage.FileStorageService;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mechanics of the files in the chat (V111): validation, storage, reading, and every path that
 * deletes one. Carries no authorisation of its own - the chat services decide who may act, exactly
 * as {@code CommentFileSupport} does for the training threads, whose rules these mirror.
 */
@Component
public class ContactFileSupport {

    private static final Logger logger = LoggerFactory.getLogger(ContactFileSupport.class);

    /**
     * Its own folder, never shared: each private folder is reconciled against ONE table by its
     * orphan sweep, and a file of the chat in another table's folder would be swept as abandoned.
     * Not mapped by the public FileController (guarded by PrivateFileFoldersTest).
     */
    public static final String FOLDER = "contactfiles";

    /** A year, like the chat's messages and the training threads' files. Stated in the privacy policy. */
    public static final Duration RETENTION = Duration.ofDays(365);

    /**
     * Live files per conversation - both sides together, and "live" means younger than the year of
     * retention, so in practice this many files a year (decision 2026-11-25). Far above a training
     * thread's 20: this conversation is one per person for the life of the account, and an active
     * athlete sending a photo a week while the coach sends plans back must not hit the wall in
     * spring. The bound on disk stays: at most 200 x 5 MB per person, in practice a fraction of it,
     * as pictures are shrunk in the browser before upload.
     */
    public static final int MAX_PER_CONVERSATION = 200;

    /** As in the training threads: these sit on disk for a year. */
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private final ContactMessageFileRepository fileRepository;
    private final ContactMessageRepository messageRepository;
    private final FileStorageService fileStorageService;

    public ContactFileSupport(ContactMessageFileRepository fileRepository,
                              ContactMessageRepository messageRepository,
                              FileStorageService fileStorageService) {
        this.fileRepository = fileRepository;
        this.messageRepository = messageRepository;
        this.fileStorageService = fileStorageService;
    }

    // ---------- writing ----------

    /** Refused before anything is written: too many files in one message, or in the conversation. */
    public void checkLimits(UUID conversationId, List<MultipartFile> files) {
        if (files.size() > ContactMessageFile.MAX_PER_MESSAGE) {
            throw new LocalizedMessage.Rejected("contact.file.too.many", ContactMessageFile.MAX_PER_MESSAGE);
        }
        if (fileRepository.countForConversation(conversationId) + files.size() > MAX_PER_CONVERSATION) {
            throw new LocalizedMessage.Conflict("contact.file.conversation.limit", MAX_PER_CONVERSATION);
        }
    }

    /**
     * Stores the uploads and hangs them off an already-persisted message. Pictures are re-encoded
     * by the storage layer, which drops EXIF (and the GPS position in it). The bytes land on disk
     * inside the caller's transaction; a rollback afterwards leaves them behind, which is what the
     * orphan sweep reconciles.
     */
    public List<ContactMessageFile> attach(ContactMessage message, List<MultipartFile> files) {
        Instant expiresAt = Instant.now().plus(RETENTION);
        List<ContactMessageFile> saved = new ArrayList<>();
        int position = 0;
        for (MultipartFile file : files) {
            FileStorageService.StoredFile stored = store(file);
            saved.add(fileRepository.save(new ContactMessageFile(
                message,
                stored.filename(),
                ContactMessageFile.sanitizeName(file.getOriginalFilename()),
                stored.mimeType(),
                stored.sizeBytes(),
                stored.width(),
                stored.height(),
                position++,
                expiresAt)));
        }
        return saved;
    }

    private FileStorageService.StoredFile store(MultipartFile file) {
        try {
            return fileStorageService.storeAttachment(file, FOLDER, MAX_BYTES);
        } catch (IOException e) {
            throw new LocalizedMessage.Conflict("contact.file.upload.failed");
        }
    }

    // ---------- reading ----------

    /** The files of a page of messages, grouped by message, in one read. */
    public Map<UUID, List<ContactMessageFile>> filesOf(Collection<UUID> messageIds) {
        Map<UUID, List<ContactMessageFile>> byMessage = new HashMap<>();
        if (messageIds.isEmpty()) return byMessage;
        for (ContactMessageFile f : fileRepository.findByMessageIds(messageIds)) {
            byMessage.computeIfAbsent(f.getMessage().getId(), k -> new ArrayList<>()).add(f);
        }
        return byMessage;
    }

    /** @param urlPrefix where this side serves files - the person's chat and the panel differ */
    public static ContactFileDto toDto(ContactMessageFile f, String urlPrefix, boolean canDelete) {
        return new ContactFileDto(
            f.getId(),
            urlPrefix + f.getId(),
            f.getMimeType(),
            f.getOriginalName(),
            f.getSizeBytes(),
            f.getWidth() == null ? null : (int) f.getWidth(),
            f.getHeight() == null ? null : (int) f.getHeight(),
            f.getExpiresAt(),
            canDelete);
    }

    public ContactMessageFile requireFile(UUID fileId) {
        return fileRepository.findByIdWithConversation(fileId)
            .orElseThrow(() -> new LocalizedMessage.Rejected("contact.file.not.found"));
    }

    /** A file opened for streaming. {@code fileName} is the escaped display name, for the download. */
    public record FileStream(InputStream inputStream, long size, String mimeType, @Nullable String fileName) {}

    /**
     * Opens a file the caller has already been allowed to read. A row that outlived its bytes (a
     * failed unlink, an interrupted sweep) answers like a missing row: there is nothing to show.
     */
    public FileStream stream(ContactMessageFile file) {
        if (!exists(file.getFilename())) {
            throw new LocalizedMessage.Rejected("contact.file.not.found");
        }
        try {
            return new FileStream(open(file.getFilename()), sizeOf(file.getFilename()),
                file.getMimeType(), file.getOriginalName());
        } catch (IOException e) {
            throw new LocalizedMessage.Rejected("contact.file.not.found");
        }
    }

    public boolean exists(String filename) {
        try {
            return fileStorageService.exists(filename, FOLDER);
        } catch (IllegalArgumentException e) {
            return false; // malformed name (path-traversal guard) - treat as missing
        }
    }

    public InputStream open(String filename) throws IOException {
        return fileStorageService.getInputStream(filename, FOLDER);
    }

    public long sizeOf(String filename) {
        return fileStorageService.getFileSize(filename, FOLDER);
    }

    // ---------- deleting ----------

    /**
     * The one deletion path, shared by the button and the retention sweep. A message that was only
     * the file goes with it - its text is null, so nothing else would stop an empty bubble from
     * sitting in the chat forever.
     */
    public void deleteFile(ContactMessageFile file) {
        ContactMessage message = file.getMessage();
        String filename = file.getFilename();

        fileRepository.delete(file);
        fileRepository.flush(); // the count below must not see the row just removed

        if (message.getBody() == null && fileRepository.countByMessageId(message.getId()) == 0) {
            messageRepository.delete(message);
        }
        unlink(filename);
    }

    /**
     * The files of a conversation about to be deleted. Must run BEFORE the delete: the rows go by
     * the database cascade, which Hibernate never loads, so nothing else can name the files.
     */
    public void purgeForConversation(UUID conversationId) {
        unlinkAll(fileRepository.findFilenamesForConversation(conversationId));
    }

    /** Same, for an account about to be deleted - called on every deletion path of an account. */
    public void purgeForUser(UUID userId) {
        unlinkAll(fileRepository.findFilenamesForUser(userId));
    }

    /** Same, for the messages the yearly sweep is about to delete by age. */
    public void purgeForMessagesBefore(Instant cutoff) {
        unlinkAll(fileRepository.findFilenamesOfMessagesBefore(cutoff));
    }

    // ---------- retention ----------

    /** @return files removed because their year is up */
    public int deleteExpired(Instant now) {
        List<ContactMessageFile> expired = fileRepository.findExpiredBefore(now);
        for (ContactMessageFile file : expired) {
            deleteFile(file);
        }
        return expired.size();
    }

    /**
     * Files in the folder no row claims, older than the grace window - what makes "gone after a
     * year" checkable: every unlink above happens after commit and only logs a failure. The grace
     * window spares an upload that landed between reading the names and listing the folder.
     */
    public int sweepOrphans(Duration grace) {
        Set<String> known = fileRepository.findAllFilenames();
        Instant cutoff = Instant.now().minus(grace);
        int deleted = 0;
        for (String filename : fileStorageService.listFilenames(FOLDER)) {
            try {
                if (known.contains(filename)) continue;
                long modified = fileStorageService.getLastModifiedMillis(filename, FOLDER);
                if (modified < 0 || Instant.ofEpochMilli(modified).isAfter(cutoff)) continue;
                fileStorageService.delete(filename, FOLDER);
                deleted++;
            } catch (Exception e) {
                logger.warn("Skipping orphan sweep of chat file {}: {}", filename, e.getMessage());
            }
        }
        return deleted;
    }

    private void unlinkAll(List<String> filenames) {
        for (String filename : filenames) {
            unlink(filename);
        }
    }

    private void unlink(@Nullable String filename) {
        if (filename == null) return;
        try {
            fileStorageService.delete(filename, FOLDER);
        } catch (Exception e) {
            // Never fatal: the orphan sweep reconciles whatever a failure leaves behind.
            logger.warn("Failed to delete chat file {}", filename, e);
        }
    }
}
