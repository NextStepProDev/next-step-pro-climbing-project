package pl.nextsteppro.climbing.infrastructure.storage;

import net.coobird.thumbnailator.Thumbnails;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

@Component
public class ImageOptimizer {
    private static final Logger log = LoggerFactory.getLogger(ImageOptimizer.class);

    private static final int MAX_DIMENSION = 1920;
    private static final double OUTPUT_QUALITY = 0.85;
    private static final long SIZE_THRESHOLD_BYTES = 2 * 1024 * 1024; // 2 MB

    /**
     * Decompression-bomb ceiling, checked against the HEADER before a single pixel is decoded.
     * A 30000×30000 PNG is a few hundred KB on the wire and ~3.6 GB decoded — on a one-core box
     * with an ~846 MB heap that is a denial of service anyone with an account could trigger.
     */
    private static final long MAX_PIXELS = 40_000_000L;

    /** Quality for the forced re-encode. Higher than the CMS default: attachments are often
     *  screenshots of a watch or an app, where JPEG artefacts land on small digits and fine text. */
    private static final double ATTACHMENT_QUALITY = 0.9;

    /**
     * Re-encodes an upload to JPEG unconditionally, which is what strips EXIF — including the GPS
     * coordinates of wherever a photo was taken — and what makes the stored dimensions and format
     * facts rather than client claims. An undecodable payload is an error here, never passed
     * through byte for byte.
     *
     * <p>The output is JPEG and not "whatever came in" because this JVM ships neither a WebP reader
     * nor a WebP writer ({@code ImageIO.getWriterFormatNames()} lists jpg/png/bmp/gif/tiff/wbmp
     * only). Keeping the input format would mean either no re-encode for WebP — no EXIF strip, and
     * no way to read back the dimensions the UI needs to reserve space — or a write that throws.
     * PNG could be kept, but then the same upload takes two different code paths for no gain.
     *
     * <p>Transparency is flattened onto white first: writing an image with an alpha channel as JPEG
     * otherwise renders it against black.
     */
    public OptimizedImage reencodeAsJpeg(InputStream inputStream) throws IOException {
        byte[] originalBytes = inputStream.readAllBytes();
        guardPixelCount(originalBytes);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(originalBytes));
        if (decoded == null) {
            // Reached when the bytes passed the signature check but no reader could handle them —
            // a truncated or corrupt file. Storing it would serve a broken image forever.
            throw new IllegalArgumentException("Image file is damaged or unreadable");
        }

        BufferedImage opaque = new BufferedImage(decoded.getWidth(), decoded.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = opaque.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, opaque.getWidth(), opaque.getHeight());
            g.drawImage(decoded, 0, 0, null);
        } finally {
            g.dispose();
        }

        var baos = new ByteArrayOutputStream();
        var builder = Thumbnails.of(opaque);
        if (opaque.getWidth() > MAX_DIMENSION || opaque.getHeight() > MAX_DIMENSION) {
            builder.size(MAX_DIMENSION, MAX_DIMENSION);
        } else {
            // size() scales UP to fill the box, so a small screenshot would come back blurry and
            // several times heavier. Only shrink; never enlarge.
            builder.scale(1.0);
        }
        builder.outputQuality(ATTACHMENT_QUALITY)
                .outputFormat("jpg")
                .toOutputStream(baos);

        byte[] bytes = baos.toByteArray();
        BufferedImage written = ImageIO.read(new ByteArrayInputStream(bytes));
        return new OptimizedImage(new ByteArrayInputStream(bytes), ".jpg",
                written != null ? written.getWidth() : null,
                written != null ? written.getHeight() : null);
    }

    public OptimizedImage optimize(InputStream inputStream, String extension) throws IOException {
        byte[] originalBytes = inputStream.readAllBytes();
        guardPixelCount(originalBytes);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(originalBytes));

        if (image == null) {
            return new OptimizedImage(new ByteArrayInputStream(originalBytes), extension, null, null);
        }

        boolean needsResize = image.getWidth() > MAX_DIMENSION || image.getHeight() > MAX_DIMENSION;
        boolean needsCompression = originalBytes.length > SIZE_THRESHOLD_BYTES;

        if (!needsResize && !needsCompression) {
            log.debug("Image already optimized ({}×{}, {} KB) — skipping", image.getWidth(), image.getHeight(), originalBytes.length / 1024);
            return new OptimizedImage(new ByteArrayInputStream(originalBytes), extension,
                    image.getWidth(), image.getHeight());
        }

        String outputFormat = outputFormat(extension);
        var baos = new ByteArrayOutputStream();

        var builder = Thumbnails.of(image);
        if (needsResize) {
            builder.size(MAX_DIMENSION, MAX_DIMENSION);
        } else {
            builder.scale(1.0);
        }
        builder.outputQuality(OUTPUT_QUALITY)
                .outputFormat(outputFormat)
                .toOutputStream(baos);

        log.info("Optimized image: {} KB → {} KB ({}×{} → max {}px, format: {})",
                originalBytes.length / 1024, baos.size() / 1024,
                image.getWidth(), image.getHeight(), MAX_DIMENSION, outputFormat);

        byte[] optimizedBytes = baos.toByteArray();
        // Read back rather than computing the expected size: Thumbnails' own rounding decides the
        // final pixels, and these numbers end up in the DB reserving layout space for the image.
        BufferedImage written = ImageIO.read(new ByteArrayInputStream(optimizedBytes));
        Integer width = written != null ? written.getWidth() : null;
        Integer height = written != null ? written.getHeight() : null;

        String newExtension = "." + outputFormat;
        return new OptimizedImage(new ByteArrayInputStream(optimizedBytes), newExtension, width, height);
    }

    /** Reads only the header — {@code getWidth}/{@code getHeight} never decode the raster. */
    private static void guardPixelCount(byte[] bytes) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (iis == null) {
                return;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return; // no reader for this format — decoding will fail on its own, harmlessly
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw new IllegalArgumentException("Image resolution is too large");
                }
            } catch (IOException e) {
                // Unreadable header: let the normal decode path produce the error instead.
                log.debug("Could not read image header for the pixel guard: {}", e.getMessage());
            } finally {
                reader.dispose();
            }
        }
    }

    private String outputFormat(String extension) {
        return switch (extension.toLowerCase()) {
            case ".png" -> "png";
            case ".webp" -> "webp";
            default -> "jpg";
        };
    }

    /** Dimensions are null when the payload was passed through undecoded (non-force mode). */
    public record OptimizedImage(InputStream inputStream, String extension,
                                 @Nullable Integer width, @Nullable Integer height) {}
}
