package ch.finyo.budget;

import java.math.BigDecimal;
import java.util.UUID;

public record MonthlyBudgetPositionResponse(
        UUID id,
        String name,
        BigDecimal amount,
        int sortOrder
) {
    public static MonthlyBudgetPositionResponse from(MonthlyBudgetPosition position) {
        return new MonthlyBudgetPositionResponse(
                position.getId(),
                position.getName(),
                position.getAmount(),
                position.getSortOrder()
        );
    }
}
