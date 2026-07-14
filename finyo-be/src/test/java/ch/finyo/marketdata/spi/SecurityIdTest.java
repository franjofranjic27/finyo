package ch.finyo.marketdata.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for SecurityId.
 *
 * Two properties are under test, and the second one is a security control:
 *
 * <ol>
 *   <li><b>Normalisation.</b> The identifiers are normalised at construction rather
 *       than at every use site, which is what lets the cache be looked up by equality:
 *       a user typing "ie00b4l5y983" and a CSV carrying " IE00B4L5Y983 " have to be the
 *       same security, or the second one silently triggers a fresh vendor call and a
 *       duplicate row.</li>
 *   <li><b>Format validation.</b> The bulk-import path deliberately skips Bean
 *       Validation — each row reports its own error instead of failing the batch — so
 *       the constructor is the only check that cannot be bypassed. Without it a
 *       500-character "ISIN" from a CSV travelled all the way to OpenFIGI, and into an
 *       unencoded SIX query string on the way.</li>
 * </ol>
 *
 * There is no {@code Ticker} variant any more, and its absence is deliberate: a ticker
 * without an exchange is ambiguous (SWDA trades on several venues), so resolving one
 * meant picking an arbitrary listing and storing it as the answer.
 */
@DisplayName("SecurityId")
class SecurityIdTest {

    @Nested
    @DisplayName("Isin")
    class Isin {

        @Test
        void uppercases_and_trims() {
            assertThat(new SecurityId.Isin("  ie00b4l5y983 ").value()).isEqualTo("IE00B4L5Y983");
        }

        @Test
        void two_isins_written_differently_are_the_same_identifier() {
            assertThat(new SecurityId.Isin("ie00b4l5y983")).isEqualTo(new SecurityId.Isin("IE00B4L5Y983"));
        }

        @ParameterizedTest(name = "accepts \"{0}\"")
        @ValueSource(strings = {
                "IE00B4L5Y983",     // iShares Core MSCI World — letters and digits in the body
                "CH0038863350",     // Nestlé — an all-digit body
                "CH0214967314"      // an unlisted CSIF 3a fund
        })
        void accepts_a_well_formed_isin(String isin) {
            assertThat(new SecurityId.Isin(isin).value()).isEqualTo(isin);
        }

        @ParameterizedTest(name = "rejects \"{0}\"")
        @ValueSource(strings = {
                "IE00B4L5Y98",              // 11 characters — one short
                "IE00B4L5Y9831",            // 13 characters — one long
                "1E00B4L5Y983",             // country code must be letters
                "IE00B4L5Y98X",             // the last character must be the check digit
                "IE00 B4L5Y983",            // whitespace inside
                "IE00B4L5Y98-",             // punctuation
                "../../etc/passwd",         // path traversal, had it reached a URL
                "IE00B4L5Y983&select=*",    // query-string injection, had it reached SIX
                "IE00B4L5Y983'--"           // quote, had it reached anything SQL-shaped
        })
        void rejects_a_malformed_isin(String malformed) {
            // The bulk path has no Bean Validation, so this constructor is the only
            // thing standing between a hostile CSV cell and an outbound vendor request.
            assertThatThrownBy(() -> new SecurityId.Isin(malformed))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ISIN");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void rejects_a_blank_value(String blank) {
            assertThatThrownBy(() -> new SecurityId.Isin(blank))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ISIN");
        }

        @Test
        void rejects_a_five_hundred_character_isin_before_any_provider_sees_it() {
            // The concrete regression: a CSV column that is not an ISIN at all used to
            // be POSTed to OpenFIGI verbatim.
            String tooLong = "A".repeat(500);

            assertThatThrownBy(() -> new SecurityId.Isin(tooLong))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void normalises_before_it_validates_so_the_value_that_reaches_a_url_is_always_ascii() {
            // "ı" (U+0131, dotless i) uppercases to plain ASCII "I" under Locale.ROOT.
            // Because the record normalises *first* and validates the normalised string,
            // the value that later gets spliced into the unencoded SIX query is the ASCII
            // one. Reverse the two steps and the regex would be checking a string that is
            // not the string that goes on the wire — the classic case-folding bypass.
            assertThat(new SecurityId.Isin("ıe00b4l5y983").value()).isEqualTo("IE00B4L5Y983");
        }
    }

    @Nested
    @DisplayName("Valor")
    class Valor {

        @Test
        void trims_but_keeps_the_digits_as_typed() {
            assertThat(new SecurityId.Valor(" 3886335 ").value()).isEqualTo("3886335");
        }

        @ParameterizedTest(name = "accepts \"{0}\"")
        @ValueSource(strings = {"1", "3886335", "24476758", "123456789012"})
        void accepts_a_well_formed_valor(String valor) {
            assertThat(new SecurityId.Valor(valor).value()).isEqualTo(valor);
        }

        @ParameterizedTest(name = "rejects \"{0}\"")
        @ValueSource(strings = {
                "NESN",                 // a ticker is not a valor
                "3886335&select=*",     // query-string injection
                "38 86335",             // whitespace inside
                "-3886335",             // sign
                "3886335.0",            // decimal point
                "1234567890123"         // 13 digits — beyond the historical bound
        })
        void rejects_a_malformed_valor(String malformed) {
            assertThatThrownBy(() -> new SecurityId.Valor(malformed))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("valor");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void rejects_a_blank_value(String blank) {
            assertThatThrownBy(() -> new SecurityId.Valor(blank))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("valor");
        }
    }
}
