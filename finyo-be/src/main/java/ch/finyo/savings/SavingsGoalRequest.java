package ch.finyo.savings;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SavingsGoalRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @DecimalMin("0.01") BigDecimal targetAmount,
        BigDecimal currentAmount,
        LocalDate targetDate,
        UUID accountId,
        @Size(max = 50) String icon,
        @Size(max = 7) String color
) {}
