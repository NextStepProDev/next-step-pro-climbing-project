package pl.nextsteppro.climbing.api.trainingcalendar;

import org.jspecify.annotations.Nullable;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.InputStream;

/**
 * The one place that builds a response for a training file. Both streams — the coach's materials
 * and the attachments people send in a thread — go through here so their headers cannot drift
 * apart, which is the failure mode CLAUDE.md records for every twinned pair in this codebase.
 *
 * <p>Deliberately unlike {@code /api/files}, which serves public media with a week of shared cache:
 * these belong to one coach/athlete pair. {@code no-store} keeps them out of Cloudflare, out of any
 * proxy in between and off the reader's disk, which costs nothing — the client holds the bytes in
 * memory for as long as the page is open.
 */
final class PrivateFileResponses {

    private PrivateFileResponses() {}

    static ResponseEntity<Resource> stream(InputStream body, long size, String mimeType,
                                           @Nullable String downloadName) {
        MediaType contentType = MediaType.parseMediaType(mimeType);
        boolean isImage = "image".equalsIgnoreCase(contentType.getType());

        return ResponseEntity.ok()
            .contentType(contentType)
            .contentLength(size)
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            // The type is what the bytes are (checked on upload, images re-encoded by us), so
            // sniffing can only make it wrong.
            .header("X-Content-Type-Options", "nosniff")
            // A PDF is rendered by a full scripting engine. Sandboxing costs nothing for an image
            // and removes the whole question for anything an athlete uploads to their own thread.
            .header("Content-Security-Policy", "sandbox")
            // Images render in place; everything else is offered as a download rather than run
            // inside our own origin.
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition(isImage, downloadName))
            .body(new InputStreamResource(body));
    }

    private static String disposition(boolean isImage, @Nullable String downloadName) {
        String type = isImage ? "inline" : "attachment";
        if (downloadName == null || downloadName.isBlank()) {
            return type;
        }
        // Quotes and backslashes would break out of the quoted-string; the name is display-only.
        String safe = downloadName.replace("\\", "").replace("\"", "").replace("\r", "").replace("\n", "");
        return type + "; filename=\"" + safe + "\"";
    }
}
