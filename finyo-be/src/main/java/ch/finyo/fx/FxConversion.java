package ch.finyo.fx;

import ch.finyo.common.money.CurrencyCode;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The result of asking {@link FxConverter} to express an amount in CHF.
 *
 * A type rather than a bare number, for the same reason {@link ch.finyo.common.SourceResult} is:
 * "converted at this rate" and "cannot be converted, no rate exists" are different facts, and the
 * caller (the portfolio total) must treat them differently — the second is excluded and flagged,
 * never quietly counted as though it were francs.
 */
public sealed interface FxConversion {

    /**
     * The amount in CHF. {@code rate}, {@code rateDate} and {@code source} are null for an identity
     * conversion — an amount already in CHF, or in an unknown currency that is taken at face value
     * rather than assigned an invented rate — and populated when a real rate was applied, so the UI
     * can show the user how the number was reached.
     */
    record Converted(BigDecimal chf,
                     @Nullable BigDecimal rate,
                     @Nullable LocalDate rateDate,
                     @Nullable FxRateType type,
                     @Nullable String source) implements FxConversion {}

    /** No rate exists for this currency at or before the date. Excluded from the total, not guessed. */
    record Unconvertible(CurrencyCode currency) implements FxConversion {}
}
