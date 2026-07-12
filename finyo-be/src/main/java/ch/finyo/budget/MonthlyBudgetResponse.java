package ch.finyo.budget;

import java.math.BigDecimal;

public record MonthlyBudgetResponse(
        BigDecimal netIncome,
        BigDecimal savings,
        BigDecimal investing,
        BigDecimal pillar3a,
        BigDecimal taxReserve,
        BigDecimal workCosts,
        BigDecimal fixedCostsPerMonth,
        BigDecimal available
) {
    public static MonthlyBudgetResponse from(MonthlyBudget budget, BigDecimal fixedCostsPerMonth) {
        return of(
                budget.getNetIncome(),
                budget.getSavings(),
                budget.getInvesting(),
                budget.getPillar3a(),
                budget.getTaxReserve(),
                budget.getWorkCosts(),
                fixedCostsPerMonth
        );
    }

    /** Default view for users without a monthly_budget row yet: all zeros, current fixed costs. */
    public static MonthlyBudgetResponse empty(BigDecimal fixedCostsPerMonth) {
        return of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, fixedCostsPerMonth);
    }

    private static MonthlyBudgetResponse of(
            BigDecimal netIncome,
            BigDecimal savings,
            BigDecimal investing,
            BigDecimal pillar3a,
            BigDecimal taxReserve,
            BigDecimal workCosts,
            BigDecimal fixedCostsPerMonth
    ) {
        // may be negative by design: the user sees an over-committed plan as-is
        BigDecimal available = netIncome
                .subtract(savings)
                .subtract(investing)
                .subtract(pillar3a)
                .subtract(taxReserve)
                .subtract(workCosts)
                .subtract(fixedCostsPerMonth);
        return new MonthlyBudgetResponse(
                netIncome, savings, investing, pillar3a, taxReserve, workCosts,
                fixedCostsPerMonth, available);
    }
}
