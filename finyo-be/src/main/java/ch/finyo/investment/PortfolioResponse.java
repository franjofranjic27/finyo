package ch.finyo.investment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record PortfolioResponse(
        List<PortfolioPositionResponse> positions,
        BigDecimal totalValue,
        BigDecimal totalCost,
        BigDecimal gainLoss,
        BigDecimal returnPct,
        OffsetDateTime asOf
) {}
