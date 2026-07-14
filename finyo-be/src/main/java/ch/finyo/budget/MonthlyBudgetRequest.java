package ch.finyo.budget;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record MonthlyBudgetRequest(
        @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal netIncome
) {}
