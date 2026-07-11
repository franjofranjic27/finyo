package ch.finyo.pillar3;

import java.math.BigDecimal;
import java.util.UUID;

public record Pillar3ProductComparison(
        UUID productId,
        String provider,
        String name,
        String isin,
        BigDecimal equityPct,
        BigDecimal terPct,
        BigDecimal avgReturnPct,
        BigDecimal netReturnPct,
        BigDecimal totalFees,
        BigDecimal finalCapital
) {}
