package ch.finyo.fx;

import ch.finyo.common.money.CurrencyCode;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Converts a foreign-currency amount to CHF, from stored rates only.
 *
 * <p><b>Reads never touch the network</b>, exactly like {@link ch.finyo.marketdata.MarketDataService}
 * — the rates are put in {@code fx_rate} by {@link FxRateSyncJob} overnight, and a conversion is a
 * database lookup. A dead Frankfurter cannot slow a portfolio page; at worst the newest rate is a
 * day old, which for a daily reference rate is no loss at all.
 *
 * <p>Three cases, and the middle one is the point:
 * <ul>
 *   <li>CHF, or an unknown currency → identity. Face value is kept; no rate is invented, and
 *       excluding an unknown-currency amount from the total would drop a real holding.</li>
 *   <li>a foreign currency with a rate at or before the date → converted, carrying the rate so the
 *       user can check the number.</li>
 *   <li>a foreign currency with no rate → {@link FxConversion.Unconvertible}. The caller excludes
 *       it and says so; it is never counted as francs.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class FxConverter {

    private final FxRateRepository repository;

    /**
     * @param on   the valuation date; the latest rate at or before it is used (never interpolated)
     * @param type MID for valuation, OFFICIAL_CH for tax — required, never defaulted (see ADR-009)
     */
    public FxConversion toChf(BigDecimal amount, @Nullable CurrencyCode currency,
                              LocalDate on, FxRateType type) {
        if (currency == null || currency.isChf()) {
            return new FxConversion.Converted(amount, null, null, null, null);
        }
        return repository
                .findTopByCurrencyAndRateTypeAndRateDateLessThanEqualOrderByRateDateDesc(
                        currency.value(), type, on)
                .<FxConversion>map(rate -> new FxConversion.Converted(
                        amount.multiply(rate.getChfPerUnit()),
                        rate.getChfPerUnit(),
                        rate.getRateDate(),
                        type,
                        rate.getSource()))
                .orElseGet(() -> new FxConversion.Unconvertible(currency));
    }
}
