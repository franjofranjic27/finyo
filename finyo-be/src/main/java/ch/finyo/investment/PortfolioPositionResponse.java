package ch.finyo.investment;

import java.math.BigDecimal;
import java.util.UUID;

public record PortfolioPositionResponse(
        UUID id,
        UUID instrumentId,
        String name,
        String isin,
        String valor,
        BigDecimal quantity,
        BigDecimal purchasePrice,
        BigDecimal currentPrice,
        PriceSource priceSource,
        BigDecimal value,
        BigDecimal gainLoss,
        BigDecimal returnPct,
        BigDecimal allocationPct
) {}
