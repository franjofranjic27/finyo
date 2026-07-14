package ch.finyo.budget;

import java.math.BigDecimal;
import java.util.List;

public record MonthlyBudgetResponse(
        BigDecimal netIncome,
        List<MonthlyBudgetPositionResponse> positions,
        BigDecimal fixedCostsPerMonth,
        BigDecimal available
) {
    public static MonthlyBudgetResponse of(
            BigDecimal netIncome,
            List<MonthlyBudgetPositionResponse> positions,
            BigDecimal fixedCostsPerMonth
    ) {
        BigDecimal allocated = positions.stream()
                .map(MonthlyBudgetPositionResponse::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // may be negative by design: the user sees an over-committed plan as-is
        BigDecimal available = netIncome.subtract(allocated).subtract(fixedCostsPerMonth);
        return new MonthlyBudgetResponse(netIncome, positions, fixedCostsPerMonth, available);
    }
}
