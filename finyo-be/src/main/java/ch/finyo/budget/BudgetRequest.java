package ch.finyo.budget;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BudgetRequest(
        @NotNull UUID categoryId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        BudgetPeriod period,
        @NotNull LocalDate validFrom,
        LocalDate validUntil
) {}
