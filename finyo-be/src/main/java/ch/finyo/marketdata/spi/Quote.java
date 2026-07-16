package ch.finyo.marketdata.spi;

import ch.finyo.common.money.CurrencyCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * One price for one security, from one source, at one point in time.
 *
 * Every field except the price itself is there to stop the price from being mistaken for
 * something it is not:
 *
 * @param currency    the price is a number <em>in a currency</em>. Summing a USD quote into
 *                    a CHF total is the bug this project already shipped once.
 * @param asOf        the trading day the price belongs to — not the day we fetched it. A
 *                    Friday close read on Sunday is three days old and must say so.
 * @param retrievedAt when we asked. Distinct from {@code asOf}: it tells us how stale our
 *                    copy is, while {@code asOf} tells us how old the price is.
 * @param delayed     SIX serves its free feed with a 15-minute delay. It is not a live quote,
 *                    and pretending otherwise would be a small lie told very often.
 */
public record Quote(
        String isin,
        BigDecimal price,
        CurrencyCode currency,
        LocalDate asOf,
        OffsetDateTime retrievedAt,
        boolean delayed,
        DataSource source
) {
}
