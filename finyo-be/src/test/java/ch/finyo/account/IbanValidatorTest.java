package ch.finyo.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for the IBAN normalization and ISO 7064 mod-97 validation.
 * All sample IBANs are the official examples published for their country.
 */
class IbanValidatorTest {

    // -------------------------------------------------------------------------
    // normalize()
    // -------------------------------------------------------------------------

    @Test
    void normalize_strips_spaces_and_uppercases() {
        assertThat(IbanValidator.normalize("ch93 0076 2011 6238 5295 7"))
                .isEqualTo("CH9300762011623852957");
    }

    @Test
    void normalize_keeps_already_normalized_input_unchanged() {
        assertThat(IbanValidator.normalize("CH9300762011623852957"))
                .isEqualTo("CH9300762011623852957");
    }

    @Test
    void normalize_returns_null_for_null_and_blank_input() {
        assertThat(IbanValidator.normalize(null)).isNull();
        assertThat(IbanValidator.normalize("")).isNull();
        assertThat(IbanValidator.normalize("   ")).isNull();
    }

    // -------------------------------------------------------------------------
    // isValid()
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "CH9300762011623852957",   // Switzerland
            "DE89370400440532013000",  // Germany
            "LT121000011101001000"     // Lithuania
    })
    void isValid_accepts_official_example_ibans(String iban) {
        assertThat(IbanValidator.isValid(iban)).isTrue();
    }

    @Test
    void isValid_accepts_input_that_was_normalized_from_spaced_lowercase_form() {
        assertThat(IbanValidator.isValid(IbanValidator.normalize("ch93 0076 2011 6238 5295 7"))).isTrue();
    }

    @Test
    void isValid_rejects_invalid_checksum() {
        // Last digit of the valid CH example flipped: format still matches,
        // only the mod-97 checksum fails.
        assertThat(IbanValidator.isValid("CH9300762011623852958")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "CH93",                      // far too short
            "9300762011623852957CH",     // country code not first
            "CH93!0762011623852957",     // illegal character
            "ch9300762011623852957",     // lowercase — isValid expects normalized input
            "CHAB00762011623852957",     // letters where check digits belong
            "CH93007620116238529571234567890123"  // longer than 34 chars
    })
    void isValid_rejects_structurally_invalid_input(String iban) {
        assertThat(IbanValidator.isValid(iban)).isFalse();
    }

    @Test
    void isValid_rejects_null() {
        assertThat(IbanValidator.isValid(null)).isFalse();
    }
}
