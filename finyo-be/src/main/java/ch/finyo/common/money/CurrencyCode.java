package ch.finyo.common.money;

import java.util.Locale;

/**
 * An ISO 4217 currency code.
 *
 * Deliberately not {@link java.util.Currency}: that class validates against the
 * JDK's locale data, which drifts between JDK releases and rejects codes we may
 * legitimately store (crypto). A validated three-letter code is all the domain
 * needs, and it maps to CHAR(3) without a converter surprise.
 */
public record CurrencyCode(String value) {

    public static final CurrencyCode CHF = new CurrencyCode("CHF");

    public CurrencyCode {
        if (value == null || value.length() != 3 || !value.chars().allMatch(Character::isLetter)) {
            throw new IllegalArgumentException("Currency code must be three letters, was: " + value);
        }
        value = value.toUpperCase(Locale.ROOT);
    }

    /** Null-tolerant factory for external data, which may omit the currency entirely. */
    public static CurrencyCode ofNullable(String value) {
        return value == null || value.isBlank() ? null : new CurrencyCode(value.trim());
    }

    public boolean isChf() {
        return CHF.value.equals(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
