package pl.nextsteppro.climbing.domain.personaltraining;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Image dimensions land in a SMALLINT (V80) and arrive as Integers from the decoder, so the entity
 * has to decide what to do with a value that does not fit. They are only a layout hint — the space
 * the thread reserves before the bytes arrive — which is why "unknown" is a better answer than a
 * wrapped one.
 */
class TrainingCommentFileTest {

    private static TrainingCommentFile fileWith(Integer width, Integer height) {
        return new TrainingCommentFile(
            null, "0198f1a0-0000-7000-8000-000000000000.jpg", "photo.jpg", "image/jpeg",
            1024L, width, height, 0, Instant.now());
    }

    @Test
    void shouldKeepDimensionsThatFitTheColumn() {
        TrainingCommentFile file = fileWith(400, 300);

        assertEquals((short) 400, file.getWidth());
        assertEquals((short) 300, file.getHeight());
    }

    @Test
    void shouldKeepTheLargestDimensionTheColumnHolds() {
        TrainingCommentFile file = fileWith(32767, 32767);

        assertEquals(Short.MAX_VALUE, file.getWidth());
        assertEquals(Short.MAX_VALUE, file.getHeight());
    }

    /**
     * A bare shortValue() WRAPS: a 40000px panorama used to be stored as a negative width, and the
     * browser was then asked to reserve a negative amount of space for it.
     */
    @Test
    void shouldRecordAnOversizedDimensionAsUnknownRatherThanWrappingIt() {
        TrainingCommentFile file = fileWith(40000, 300);

        assertNull(file.getWidth());
        assertNull(file.getHeight());
    }

    /**
     * Both or neither — chk_tcf_dimensions. Dropping only the unusable half would trade a wrong
     * number for a constraint violation, i.e. a 500 on upload.
     */
    @Test
    void shouldDropBothDimensionsWhenOnlyOneIsOutOfRange() {
        TrainingCommentFile file = fileWith(300, 90000);

        assertNull(file.getWidth());
        assertNull(file.getHeight());
    }

    @Test
    void shouldLeaveADocumentWithoutDimensions() {
        TrainingCommentFile file = fileWith(null, null);

        assertNull(file.getWidth());
        assertNull(file.getHeight());
    }
}
