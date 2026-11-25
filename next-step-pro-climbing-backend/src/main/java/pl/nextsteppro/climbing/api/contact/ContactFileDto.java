package pl.nextsteppro.climbing.api.contact;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * A file in a chat message, as both sides show it. Public because the team's panel lists the same
 * files; {@code url} is the side's own endpoint, so neither side links to the other's.
 *
 * @param fileName  the original name, HTML-escaped at upload - decode before display
 * @param width     with {@code height}, set for a picture so the chat can reserve its space
 * @param expiresAt when the yearly retention removes it, shown next to the file
 * @param canDelete whether this viewer may remove it (the sender, or the team)
 */
public record ContactFileDto(
    UUID id,
    String url,
    String mimeType,
    @Nullable String fileName,
    long sizeBytes,
    @Nullable Integer width,
    @Nullable Integer height,
    Instant expiresAt,
    boolean canDelete
) {}
