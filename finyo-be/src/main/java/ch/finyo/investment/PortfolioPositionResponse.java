package ch.finyo.investment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

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
        BigDecimal quantity,
        BigDecimal purchasePrice,
        LocalDate purchaseDate,
        BigDecimal currentPrice,
        PriceSource priceSource,
        BigDecimal value,
        BigDecimal gainLoss,
        BigDecimal returnPct,
        BigDecimal allocationPct
) {}
