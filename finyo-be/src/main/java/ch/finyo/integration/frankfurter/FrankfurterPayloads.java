package ch.finyo.integration.frankfurter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Raw Frankfurter payloads, kept inside the adapter package.
 *
 * Frankfurter returns rates with {@code base=CHF}, i.e. <em>units of the foreign currency per one
 * CHF</em> (EUR 1.0796 per CHF). The domain stores the opposite — CHF per unit — so the adapter
 * inverts every value before it builds an {@link ch.finyo.fx.FxRate}. That direction flip lives
 * here and nowhere else. See ADR-009.
 */
final class FrankfurterPayloads {

    private FrankfurterPayloads() {}

    /**
     * A single day: {@code {"base":"CHF","date":"2026-07-14","rates":{"EUR":1.0796}}}. When the
     * requested day is a weekend or holiday Frankfurter answers with the most recent working day,
     * so {@code date} is the day the rate actually belongs to.
     */
    record Latest(String base, LocalDate date, Map<String, BigDecimal> rates) {}

    /**
     * A date range, keyed by day:
     * {@code {"base":"CHF","rates":{"2025-01-02":{"EUR":1.04},"2025-01-03":{"EUR":1.05}}}}.
     * Non-trading days are simply absent — no entry, never a filled-in gap.
     */
    record Range(String base, Map<String, Map<String, BigDecimal>> rates) {}
}
