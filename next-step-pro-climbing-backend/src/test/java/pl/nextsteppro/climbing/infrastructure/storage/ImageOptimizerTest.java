package pl.nextsteppro.climbing.infrastructure.storage;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ImageOptimizerTest {

    private final ImageOptimizer optimizer = new ImageOptimizer();

    @Test
    void shouldSkipSmallImage() throws IOException {
        byte[] imageBytes = createTestImage(800, 600);
        var result = optimizer.optimize(new ByteArrayInputStream(imageBytes), ".jpg");

        assertEquals(".jpg", result.extension());
        assertNotNull(result.inputStream());
    }

    @Test
    void shouldResizeLargeImage() throws IOException {
        byte[] imageBytes = createTestImage(4000, 3000);
        var result = optimizer.optimize(new ByteArrayInputStream(imageBytes), ".jpg");

        assertEquals(".jpg", result.extension());
        BufferedImage output = ImageIO.read(result.inputStream());
        assertNotNull(output);
        assertTrue(output.getWidth() <= 1920);
        assertTrue(output.getHeight() <= 1920);
    }

    @Test
    void shouldPreservePngExtension() throws IOException {
        byte[] imageBytes = createTestImage(3000, 2000, "png");
        var result = optimizer.optimize(new ByteArrayInputStream(imageBytes), ".png");

        assertEquals(".png", result.extension());
    }

    @Test
    void shouldPassThroughNonImageData() throws IOException {
        byte[] garbage = "not an image".getBytes();
        var result = optimizer.optimize(new ByteArrayInputStream(garbage), ".jpg");

        assertEquals(".jpg", result.extension());
        assertNotNull(result.inputStream());
    }

    private byte[] createTestImage(int width, int height) throws IOException {
        return createTestImage(width, height, "jpg");
    }

    private byte[] createTestImage(int width, int height, String format) throws IOException {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.fillRect(0, 0, width, height);
        g.dispose();
        var baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }
}
