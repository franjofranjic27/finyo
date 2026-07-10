package ch.finyo.pillar3;

import java.math.BigDecimal;
import java.util.List;

/**
 * Comparison result. {@code maxAnnualContribution} is included so the
 * frontend reads the legal cap from the API instead of hardcoding it.
 * Products are sorted by {@code finalCapital} descending (index 0 = top).
 */
public record Pillar3CompareResponse(
        BigDecimal totalPaidIn,
        BigDecimal maxAnnualContribution,
        List<Pillar3ProductComparison> products
) {}
