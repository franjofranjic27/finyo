package ch.finyo.account;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizes and validates IBANs (format + ISO 7064 mod-97 checksum).
 *
 * Package-private on purpose: IBAN handling is an implementation detail of
 * the account slice, exposed only through AccountService.
 */
final class IbanValidator {

    private static final Pattern IBAN_FORMAT = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$");

    private IbanValidator() {
    }

    /**
     * Strips all whitespace and uppercases, e.g. "ch93 0076 …" → "CH930076…".
     * Returns {@code null} for null or blank input.
     */
    static String normalize(String iban) {
        if (iban == null || iban.isBlank()) {
            return null;
        }
        return iban.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    /**
     * Validates a normalized IBAN: structural format plus the ISO 7064
     * mod-97 checksum. Null input is invalid.
     */
    static boolean isValid(String normalizedIban) {
        if (normalizedIban == null || !IBAN_FORMAT.matcher(normalizedIban).matches()) {
            return false;
        }
        return mod97(normalizedIban.substring(4) + normalizedIban.substring(0, 4)) == 1;
    }

    /**
     * Incremental mod-97 over the rearranged IBAN with letters expanded to
     * two digits (A=10 … Z=35). Avoids BigInteger by reducing after every
     * character — the intermediate value never exceeds 97 * 100 + 35.
     */
    private static int mod97(String rearranged) {
        int remainder = 0;
        for (char c : rearranged.toCharArray()) {
            if (Character.isDigit(c)) {
                remainder = (remainder * 10 + (c - '0')) % 97;
            } else {
                remainder = (remainder * 100 + (c - 'A' + 10)) % 97;
            }
        }
        return remainder;
    }
}
