package pl.nextsteppro.climbing.domain.settlement;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Grosze arithmetic shared by every money column: all of them are {@code NUMERIC(10,2)}. */
public final class Amounts {

    public static final int SCALE = 2;

    private Amounts() {}

    /** To the column's scale, so a figure cannot be stored as one amount and read back as another. */
    public static BigDecimal scale(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Scaled, then checked against the range the table's CHECK allows, so the caller gets a
     * translated message instead of a constraint name.
     *
     * @throws IllegalArgumentException when the amount falls outside {@code [min, max]}
     */
    static BigDecimal normalize(BigDecimal amount, BigDecimal min, BigDecimal max, String outOfRangeMessage) {
        BigDecimal scaled = scale(amount);
        if (scaled.compareTo(min) < 0 || scaled.compareTo(max) > 0) {
            throw new IllegalArgumentException(outOfRangeMessage);
        }
        return scaled;
    }
}
