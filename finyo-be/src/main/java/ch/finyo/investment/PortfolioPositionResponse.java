package ch.finyo.investment;

import ch.finyo.fx.FxRateType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of the portfolio.
 *
 * <p>Two currencies live here on purpose. {@code value} is in the position's own {@code currency}
 * — the honest number for the price the user paid and the value the market puts on it. {@code
 * valueChf} is that value converted to CHF, which is what the portfolio total is built from and
 * what the allocation share is a fraction of. Everything CHF-denominated (valueChf, gainLoss,
 * returnPct, allocationPct, the fx fields) is null when the position could not be converted — no
 * rate is stored for its currency yet — rather than guessed, because a guessed rate is exactly the
 * bug this module exists to prevent.
 *
 * @param currency     trading currency, or null when nobody has established it. Not defaulted to
 *                     CHF — see ADR-008.
 * @param value        the position's value in its own currency
 * @param valueChf     the value in CHF; null when unconvertible
 * @param gainLoss     CHF gain/loss (value − cost, both in CHF); null when unconvertible
 * @param returnPct    CHF return; null when unconvertible
 * @param allocationPct share of the CHF total; null when unconvertible
 * @param fxRate       the CHF-per-unit rate applied to reach valueChf; null for a CHF or
 *                     unknown-currency position, where no conversion happened
 * @param fxRateDate   the day the applied rate belongs to; null when no rate was applied
 * @param fxRateType   which kind of rate was applied (MID); null when none was
 * @param priceAsOf    the trading day the price belongs to, not the day it was fetched
 * @param stale        the price is older than a market price should be
 */
public record PortfolioPositionResponse(
        // id and positionId carry the same value: id is kept for the existing
        // UI, positionId matches the position-detail API contract.
        UUID id,
        UUID positionId,
        UUID instrumentId,
        AssetClass assetClass,
        String name,
        String isin,
        String valor,
        String currency,
        BigDecimal quantity,
        BigDecimal purchasePrice,
        LocalDate purchaseDate,
        BigDecimal currentPrice,
        PriceSource priceSource,
        LocalDate priceAsOf,
        boolean stale,
        BigDecimal value,
        BigDecimal valueChf,
        BigDecimal gainLoss,
        BigDecimal returnPct,
        BigDecimal allocationPct,
        BigDecimal fxRate,
        LocalDate fxRateDate,
        FxRateType fxRateType
) {}
