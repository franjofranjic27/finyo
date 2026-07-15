package ch.finyo.marketdata.spi;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One day's close in a price history.
 *
 * Deliberately no currency: SIX's history feed (charts.json) carries only dates and closes,
 * not the trading currency. An instrument trades in one currency across its whole history, so
 * the currency is resolved once — from the current quote — and applied to every bar, rather
 * than repeated on a field the source does not provide.
 */
public record PriceBar(LocalDate date, BigDecimal close) {
}
