package ch.finyo.investment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @param currency  trading currency, or null when nobody has established it. Not defaulted to CHF
 *                  — see ADR-008.
 * @param priceAsOf the trading day the price belongs to, not the day it was fetched
 * @param stale     the price is older than a market price should be. Not an error (an unlisted
 *                  fund or a long weekend both produce one) — but the user is told rather than
 *                  shown an old number as if it were today's.
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
        BigDecimal gainLoss,
        BigDecimal returnPct,
        BigDecimal allocationPct
) {}
