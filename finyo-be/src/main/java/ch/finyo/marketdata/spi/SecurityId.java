package ch.finyo.marketdata.spi;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * How a security is identified towards a provider.
 *
 * Sealed on purpose: providers differ in what they can resolve — OpenFIGI has no
 * concept of a Swiss valor number — and {@link SecurityReferenceProvider#supports}
 * has to answer that per identifier type. A bare String would push the decision into
 * stringly-typed guesswork.
 *
 * The format is enforced here, in the constructor, and not at the call sites. It is
 * the only place that cannot be forgotten: the bulk-import path deliberately skips
 * bean validation (each row reports its own error instead of failing the batch), so a
 * 500-character "ISIN" reaches the providers unless the type itself refuses it.
 *
 * There is no {@code Ticker} variant. A ticker without an exchange is ambiguous — SWDA
 * exists on several venues — so resolving one would mean picking an arbitrary listing
 * and calling it the answer. If tickers are ever needed, they need a MIC alongside.
 */
public sealed interface SecurityId {

    /** The identifier as it goes on the wire. */
    String value();

    /** Two letters of country code, nine alphanumeric characters, one check digit. */
    Pattern ISIN_FORMAT = Pattern.compile("^[A-Z]{2}[A-Z0-9]{9}[0-9]$");

    /** Swiss valor numbers are digits, historically up to 12 of them. */
    Pattern VALOR_FORMAT = Pattern.compile("^\\d{1,12}$");

    record Isin(String value) implements SecurityId {
        public Isin {
            value = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            if (!ISIN_FORMAT.matcher(value).matches()) {
                throw new IllegalArgumentException("Not a well-formed ISIN: " + value);
            }
        }
    }

    record Valor(String value) implements SecurityId {
        public Valor {
            value = value == null ? "" : value.trim();
            if (!VALOR_FORMAT.matcher(value).matches()) {
                throw new IllegalArgumentException("Not a well-formed valor number: " + value);
            }
        }
    }
}
