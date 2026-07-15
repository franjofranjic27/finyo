package ch.finyo.marketdata;

import ch.finyo.common.money.CurrencyCode;
import ch.finyo.marketdata.spi.DataSource;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A price handed to the rest of the application, with everything needed to judge it.
 *
 * @param stale the price is older than a market price has any business being. Not an error —
 *              an unlisted fund or a long weekend both produce one legitimately — but the
 *              user is told, instead of being shown a three-week-old number as though it
 *              were today's.
 */
public record PricePoint(
        BigDecimal price,
        CurrencyCode currency,
        LocalDate asOf,
        DataSource source,
        boolean stale
) {
}
