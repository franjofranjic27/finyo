package ch.finyo.budget;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BudgetStatusResponse(
        UUID id,
        UUID categoryId,
        String categoryName,
        String categoryColor,
        BigDecimal budgetAmount,
        BigDecimal spent,
        BigDecimal remaining,
        double percentageUsed,
        boolean overBudget,
        BudgetPeriod period,
        LocalDate validFrom,
        LocalDate validUntil
) {}
