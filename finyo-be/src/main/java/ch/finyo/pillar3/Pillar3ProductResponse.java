package ch.finyo.pillar3;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Product master data plus the derived return percentages
 * ({@link Pillar3ReturnModel}) so the frontend never duplicates the formula.
 */
public record Pillar3ProductResponse(
        UUID id,
        String provider,
        String name,
        String isin,
        String valor,
        BigDecimal equityPct,
        BigDecimal terPct,
        boolean active,
        int sortOrder,
        BigDecimal expectedReturnPct,
        BigDecimal netReturnPct
) {}
