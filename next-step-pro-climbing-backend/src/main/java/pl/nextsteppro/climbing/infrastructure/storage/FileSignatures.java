package pl.nextsteppro.climbing.infrastructure.storage;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * Recognises a file by its leading bytes.
 *
 * <p>Everything else an upload carries — the declared content type and the extension — is written
 * by the client. The bytes are the only part it cannot lie about, and until this class existed they
 * were never looked at: {@code storeDocument} wrote <em>any</em> payload as {@code .pdf} as long as
 * the request claimed {@code application/pdf}, and the served content type is derived from the
 * extension, so the file was then handed back out described as something it is not.
 */
public final class FileSignatures {

    /** Enough for every signature below; WebP needs 12 (RIFF····WEBP). */
    public static final int HEADER_BYTES = 12;

    private FileSignatures() {}

    public enum Format {
        JPEG("image/jpeg", ".jpg", ".jpeg"),
        PNG("image/png", ".png"),
        WEBP("image/webp", ".webp"),
        PDF("application/pdf", ".pdf");

        private final String contentType;
        private final String[] extensions;

        Format(String contentType, String... extensions) {
            this.contentType = contentType;
            this.extensions = extensions;
        }

        public String contentType() {
            return contentType;
        }

        public boolean isImage() {
            return this != PDF;
        }

        public boolean matchesExtension(@Nullable String extension) {
            if (extension == null) return false;
            String normalised = extension.toLowerCase(Locale.ROOT);
            return Arrays.asList(extensions).contains(normalised);
        }

        public boolean matchesContentType(@Nullable String declared) {
            return declared != null && contentType.equalsIgnoreCase(declared.trim());
        }
    }

    /** Reads at most {@link #HEADER_BYTES} without consuming the caller's stream position twice. */
    public static byte[] readHeader(InputStream in) throws IOException {
        return in.readNBytes(HEADER_BYTES);
    }

    /** The format the bytes actually are, or {@code null} for anything we do not accept. */
    @Nullable
    public static Format sniff(byte[] header) {
        if (startsWith(header, 0xFF, 0xD8, 0xFF)) {
            return Format.JPEG;
        }
        if (startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Format.PNG;
        }
        // RIFF container whose form type is WEBP — "RIFF" alone is also AVI and WAV.
        if (startsWith(header, 'R', 'I', 'F', 'F') && matchesAt(header, 8, 'W', 'E', 'B', 'P')) {
            return Format.WEBP;
        }
        if (startsWith(header, '%', 'P', 'D', 'F', '-')) {
            return Format.PDF;
        }
        return null;
    }

    private static boolean startsWith(byte[] header, int... expected) {
        return matchesAt(header, 0, expected);
    }

    private static boolean matchesAt(byte[] header, int offset, int... expected) {
        if (header.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((header[offset + i] & 0xFF) != (expected[i] & 0xFF)) {
                return false;
            }
        }
        return true;
    }
}
