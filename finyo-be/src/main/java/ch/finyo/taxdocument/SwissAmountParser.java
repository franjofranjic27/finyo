package ch.finyo.taxdocument;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Parses amounts in the formats found on Swiss tax documents:
 * {@code 52592}, {@code 2'328.00}, {@code 17,999}, {@code 55'673.24},
 * including U+2019 apostrophes and (narrow) non-breaking spaces as
 * grouping separators. Returns {@code null} for anything unparsable.
 */
final class SwissAmountParser {

    /** Apostrophes (ASCII + U+2019), spaces, NBSP (U+00A0) and narrow NBSP (U+202F). */
    private static final Pattern GROUPING_CHARS = Pattern.compile("['’   ]");
    private static final Pattern VALID_NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");

    private SwissAmountParser() {
    }

    static @Nullable BigDecimal parse(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = GROUPING_CHARS.matcher(raw.strip()).replaceAll("");
        cleaned = normalizeCommas(cleaned);
        if (!VALID_NUMBER.matcher(cleaned).matches()) {
            return null;
        }
        return new BigDecimal(cleaned);
    }

    /**
     * A comma followed by exactly 3 digits (and no dot) is a thousands
     * separator; a trailing comma group of 1-2 digits is a decimal separator.
     */
    private static String normalizeCommas(String value) {
        int lastComma = value.lastIndexOf(',');
        if (lastComma < 0) {
            return value;
        }
        String afterLastComma = value.substring(lastComma + 1);
        boolean hasDot = value.indexOf('.') >= 0;
        boolean decimalComma = !hasDot
                && !afterLastComma.isEmpty()
                && afterLastComma.length() <= 2
                && afterLastComma.chars().allMatch(Character::isDigit);
        if (decimalComma) {
            return value.substring(0, lastComma).replace(",", "") + "." + afterLastComma;
        }
        return value.replace(",", "");
    }
}
