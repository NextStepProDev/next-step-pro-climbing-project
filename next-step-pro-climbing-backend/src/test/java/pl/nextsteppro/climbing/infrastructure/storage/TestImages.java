package pl.nextsteppro.climbing.infrastructure.storage;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Real image bytes for storage tests.
 *
 * <p>These tests used to upload strings like {@code "fake image content"} labelled
 * {@code image/jpeg}. That passed, which was exactly the problem: nothing looked at the bytes, so
 * an upload could claim to be anything. Since the signature check went in, a fixture has to be the
 * thing it says it is — which is the point of the check.
 */
public final class TestImages {

    private TestImages() {}

    public static byte[] jpeg(int width, int height) {
        return encode(opaque(width, height), "jpg");
    }

    public static byte[] jpeg() {
        return jpeg(64, 48);
    }

    public static byte[] png(int width, int height) {
        return encode(opaque(width, height), "png");
    }

    public static byte[] png() {
        return png(64, 48);
    }

    /**
     * Minimal RIFF/WEBP header. The JVM has no WebP reader, so a real one could not be produced
     * here anyway — and the code paths that accept WebP never decode it, they only check the
     * signature and pass the bytes through.
     */
    public static byte[] webp() {
        byte[] header = new byte[64];
        byte[] riff = "RIFF".getBytes();
        byte[] webp = "WEBP".getBytes();
        System.arraycopy(riff, 0, header, 0, 4);
        System.arraycopy(webp, 0, header, 8, 4);
        return header;
    }

    public static byte[] pdf() {
        return "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF\n".getBytes();
    }

    private static BufferedImage opaque(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.DARK_GRAY);
            g.fillRect(0, 0, width, height);
            g.setColor(Color.WHITE);
            g.drawLine(0, 0, width, height);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
