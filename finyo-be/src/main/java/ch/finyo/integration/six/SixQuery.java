package ch.finyo.integration.six;

import ch.finyo.marketdata.spi.SecurityId;

import java.util.regex.Pattern;

/** Builds FQS {@code where} filters and normalises the column-oriented values FQS returns. */
final class SixQuery {

    /**
     * FQS expects its filter unencoded ({@code where=ISIN=CH0038863350}) — the "=" inside
     * the value is part of the syntax, so the URI cannot be percent-encoded wholesale.
     * That makes the identifier a string spliced straight into a URL.
     *
     * {@link SecurityId} already enforces the ISIN and valor formats in its constructors,
     * so nothing unsafe should ever arrive here. This whitelist is the second line of
     * defence at the point where it actually matters — the splice — and it exists because
     * the day someone relaxes the format check "just for crypto", this must still hold.
     *
     * Note the order: {@code SecurityId} normalises (trim, uppercase) <em>before</em>
     * validating, and the same normalised value is what lands in the URL. Reversing that
     * would reopen the Unicode case-folding bypass (ı→I, ﬁ→FI) — the invariant is worth
     * knowing about before anyone rearranges it.
     */
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z0-9]{1,20}$");

    private SixQuery() {
    }

    /** @throws IllegalStateException if an identifier ever reaches the URL unsanitised */
    static String filterFor(SecurityId id) {
        if (!SAFE_IDENTIFIER.matcher(id.value()).matches()) {
            throw new IllegalStateException("Refusing to put a non-alphanumeric identifier into a SIX URL");
        }
        String column = switch (id) {
            case SecurityId.Isin _ -> "ISIN";
            case SecurityId.Valor _ -> "ValorNumber";
        };
        return column + "=" + id.value();
    }

    /**
     * FQS types its cells loosely — the valor arrives as a JSON number ({@code 3886335}),
     * the ISIN as a string. Everything the domain keeps is text.
     */
    static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
