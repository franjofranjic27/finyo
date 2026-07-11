package ch.finyo.taxdocument;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SwissAmountParserTest {

    @ParameterizedTest
    @CsvSource({
            "52592,        52592",
            "2'328.00,     2328.00",
            "55'673.24,    55673.24",
            "89'666.50,    89666.50",
            "180.20,       180.20",
            "8.75,         8.75",
            "0,            0",
            "-500,         -500",
            "1'234'567.89, 1234567.89",
    })
    void parsesSwissApostropheAndPlainFormats(String raw, BigDecimal expected) {
        assertThat(SwissAmountParser.parse(raw)).isEqualByComparingTo(expected);
    }

    @Test
    void treatsCommaWithThreeDigitsAndNoDotAsThousandsSeparator() {
        assertThat(SwissAmountParser.parse("17,999")).isEqualByComparingTo("17999");
        assertThat(SwissAmountParser.parse("24,750")).isEqualByComparingTo("24750");
        assertThat(SwissAmountParser.parse("1,234,567")).isEqualByComparingTo("1234567");
    }

    @Test
    void treatsCommaWithOneOrTwoTrailingDigitsAsDecimalSeparator() {
        assertThat(SwissAmountParser.parse("123,45")).isEqualByComparingTo("123.45");
        assertThat(SwissAmountParser.parse("7,5")).isEqualByComparingTo("7.5");
        assertThat(SwissAmountParser.parse("1,234,56")).isEqualByComparingTo("1234.56");
    }

    @Test
    void treatsCommaAsThousandsSeparatorWhenDotIsPresent() {
        assertThat(SwissAmountParser.parse("1,234.50")).isEqualByComparingTo("1234.50");
    }

    @Test
    void handlesUnicodeApostropheGrouping() {
        assertThat(SwissAmountParser.parse("55’673.24")).isEqualByComparingTo("55673.24");
    }

    @Test
    void handlesNonBreakingSpaceGrouping() {
        assertThat(SwissAmountParser.parse("1 234.50")).isEqualByComparingTo("1234.50");
        assertThat(SwissAmountParser.parse("1 234.50")).isEqualByComparingTo("1234.50");
        assertThat(SwissAmountParser.parse("1 234.50")).isEqualByComparingTo("1234.50");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "CHF", "12.34.56", "1'2x3", "31.12.2025 total", "--5", "."})
    void returnsNullForGarbage(String raw) {
        assertThat(SwissAmountParser.parse(raw)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void returnsNullForNullAndBlank(String raw) {
        assertThat(SwissAmountParser.parse(raw)).isNull();
    }
}
