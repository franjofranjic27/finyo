package ch.finyo.common.money;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for CurrencyCode.
 *
 * The type exists to make "a USD position summed as CHF" impossible, so the tests
 * are about the guarantee it gives its callers: a CurrencyCode either is a valid,
 * normalised three-letter code, or it does not come into existence.
 */
@DisplayName("CurrencyCode")
class CurrencyCodeTest {

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        void accepts_a_three_letter_code() {
            assertThat(new CurrencyCode("USD").value()).isEqualTo("USD");
        }

        @Test
        void normalises_a_lowercase_code_to_uppercase() {
            // The provider wire format is not guaranteed to be uppercase, but two
            // CurrencyCodes for the same currency must be equal — otherwise an FX
            // lookup keyed by currency would miss.
            assertThat(new CurrencyCode("chf")).isEqualTo(CurrencyCode.CHF);
        }

        @ParameterizedTest(name = "rejects \"{0}\"")
        @NullSource
        @ValueSource(strings = {"", "CH", "CHFX", "CH1", "US$", "   "})
        void rejects_anything_that_is_not_three_letters(String invalid) {
            assertThatThrownBy(() -> new CurrencyCode(invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("three letters");
        }
    }

    @Nested
    @DisplayName("ofNullable")
    class OfNullable {

        @Test
        void returns_null_for_a_null_value() {
            // OpenFIGI carries no currency at all — absence must stay absence rather
            // than silently becoming CHF here; that default belongs to the caller.
            assertThat(CurrencyCode.ofNullable(null)).isNull();
        }

        @Test
        void returns_null_for_a_blank_value() {
            assertThat(CurrencyCode.ofNullable("   ")).isNull();
        }

        @Test
        void trims_and_normalises_a_padded_lowercase_value() {
            // Postgres CHAR columns and vendor payloads both pad.
            assertThat(CurrencyCode.ofNullable(" usd ")).isEqualTo(new CurrencyCode("USD"));
        }

        @Test
        void still_rejects_a_present_but_invalid_value() {
            assertThatThrownBy(() -> CurrencyCode.ofNullable("EURO"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("isChf")
    class IsChf {

        @Test
        void is_true_for_the_base_currency() {
            assertThat(CurrencyCode.CHF.isChf()).isTrue();
        }

        @Test
        void is_true_regardless_of_the_input_casing() {
            assertThat(new CurrencyCode("chf").isChf()).isTrue();
        }

        @Test
        void is_false_for_a_foreign_currency() {
            assertThat(new CurrencyCode("USD").isChf()).isFalse();
        }
    }

    @Test
    void renders_as_the_bare_code() {
        // Logged and concatenated into messages all over the place — "CHF", not
        // "CurrencyCode[value=CHF]".
        assertThat(new CurrencyCode("EUR")).hasToString("EUR");
    }
}
