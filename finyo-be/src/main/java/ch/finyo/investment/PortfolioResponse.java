package ch.finyo.investment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The aggregated portfolio. All totals are in CHF: a position's value is converted from its own
 * currency at the aggregation boundary (see {@link PortfolioService}), which is what stops a USD
 * holding from being summed as though it were francs.
 *
 * @param totalCurrency  always {@code "CHF"} — named rather than assumed, so a reader of the JSON
 *                       knows which currency the totals are in
 * @param hasUnconverted true when at least one position could not be converted (no rate stored for
 *                       its currency yet), so it is excluded from the totals and the UI can say the
 *                       total is partial rather than quietly dropping the holding
 */
public record PortfolioResponse(
        List<PortfolioPositionResponse> positions,
        BigDecimal totalValue,
        BigDecimal totalCost,
        BigDecimal gainLoss,
        BigDecimal returnPct,
        String totalCurrency,
        boolean hasUnconverted,
        OffsetDateTime asOf
) {}
