package ch.finyo.investment;

import java.util.List;

/**
 * The stored daily closes for a position's instrument.
 *
 * @param currency the currency the closes are in, or null when the instrument's currency was
 *                 never established. The chart labels its axis with it rather than assuming CHF.
 * @param points   oldest first; empty when the instrument is unlisted (no provider has history)
 *                 or was only just added and the backfill has not run yet
 */
public record PriceHistoryResponse(String isin, String currency, List<PriceHistoryPoint> points) {

    public record PriceHistoryPoint(java.time.LocalDate date, java.math.BigDecimal close) {
    }
}
