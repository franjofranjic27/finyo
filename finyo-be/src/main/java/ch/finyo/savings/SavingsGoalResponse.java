package ch.finyo.savings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SavingsGoalResponse(
        UUID id,
        String name,
        BigDecimal targetAmount,
        BigDecimal currentAmount,
        LocalDate targetDate,
        UUID accountId,
        String accountName,
        String icon,
        String color,
        boolean archived,
        double progressPercentage,
        BigDecimal monthlyRequired
) {}
