package ch.finyoapi.tax;

import java.math.BigDecimal;

public record Pillar3YearlyProjection(
        int year,
        BigDecimal balance,
        BigDecimal totalContributed,
        BigDecimal totalReturns
) {}
