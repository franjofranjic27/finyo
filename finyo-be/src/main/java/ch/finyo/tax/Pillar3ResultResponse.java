package ch.finyo.tax;

import java.math.BigDecimal;
import java.util.List;

public record Pillar3ResultResponse(
        // Current year
        BigDecimal annualContribution,
        BigDecimal maxContribution,
        boolean isMaxContribution,
        BigDecimal remainingUntilMax,

        // Tax saving
        BigDecimal annualTaxSaving,
        BigDecimal taxSavingIfMaxContribution,

        // Future value projection
        BigDecimal projectedBalanceAtRetirement,
        BigDecimal totalContributionsOverPeriod,
        BigDecimal totalReturnsOverPeriod,

        // Payout tax estimate (preferential flat rate ~2–5% depending on canton and amount)
        BigDecimal estimatedPayoutTax,
        BigDecimal netValueAfterPayoutTax,

        // Comparison: same gross contributions invested in a taxable account
        BigDecimal equivalentTaxableSavings,

        // Year-by-year chart data
        List<Pillar3YearlyProjection> yearlyProjection
) {}
