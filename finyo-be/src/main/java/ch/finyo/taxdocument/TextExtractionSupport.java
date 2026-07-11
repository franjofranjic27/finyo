package ch.finyo.taxdocument;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared line-oriented helpers for the document extractors.
 *
 * <p>The amount-token pattern deliberately excludes tokens glued to words,
 * dots, hyphens or parentheses so that dates (31.12.2025), identifiers
 * (DA-1, 756.1234.5678.97) and footnote markers ((5)) never count as amounts.
 */
final class TextExtractionSupport {

    static final Pattern AMOUNT_TOKEN = Pattern.compile(
            "(?<![\\w.\\-(])(?:\\d{1,3}(?:['\\u2019,]\\d{3})+(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)(?![\\w.)])");

    private static final List<Pattern> TAX_YEAR_PATTERNS = List.of(
            Pattern.compile("Steuerjahr\\s+(20\\d{2})"),
            Pattern.compile("31\\.12\\.(20\\d{2})"),
            Pattern.compile("01\\.01\\.(20\\d{2})"),
            Pattern.compile("\\bD\\s+(20\\d{2})\\b"));

    private TextExtractionSupport() {
    }

    static List<String> lines(String text) {
        return text.lines().toList();
    }

    static @Nullable Integer detectTaxYear(String text) {
        for (Pattern pattern : TAX_YEAR_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return null;
    }

    static List<BigDecimal> amountTokens(String line, int fromIndex) {
        List<BigDecimal> tokens = new ArrayList<>();
        Matcher matcher = AMOUNT_TOKEN.matcher(line);
        while (matcher.find()) {
            if (matcher.start() < fromIndex) {
                continue;
            }
            BigDecimal value = SwissAmountParser.parse(matcher.group());
            if (value != null) {
                tokens.add(value);
            }
        }
        return tokens;
    }

    /**
     * Returns the last amount token after {@code labelPattern} on the first
     * label line that carries one; if a label line has no amount, the next
     * {@code lookAheadLines} lines are scanned (some forms, e.g. Lohnausweis
     * Ziffer 10.1, place the value on a following line) before falling
     * through to the next occurrence of the label.
     */
    static @Nullable BigDecimal findAmountAfterLabel(List<String> lines, Pattern labelPattern, int lookAheadLines) {
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = labelPattern.matcher(lines.get(i));
            if (!matcher.find()) {
                continue;
            }
            List<BigDecimal> sameLine = amountTokens(lines.get(i), matcher.end());
            if (!sameLine.isEmpty()) {
                return sameLine.getLast();
            }
            for (int j = i + 1; j <= Math.min(i + lookAheadLines, lines.size() - 1); j++) {
                List<BigDecimal> tokens = amountTokens(lines.get(j), 0);
                if (!tokens.isEmpty()) {
                    return tokens.getLast();
                }
            }
        }
        return null;
    }

    static int indexOfFirstMatch(List<String> lines, Pattern pattern, int fromLine) {
        for (int i = fromLine; i < lines.size(); i++) {
            if (pattern.matcher(lines.get(i)).find()) {
                return i;
            }
        }
        return -1;
    }
}
