package ch.finyo.salary;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * PUT payload for the salary profile. A null {@code inputMode} defaults to
 * MONTHLY. Which gross field is required depends on the mode (MONTHLY needs
 * grossMonthly, YEARLY needs grossYearly) and is enforced in the service;
 * the counterpart is derived on upsert.
 */
public record SalaryRequest(
        SalaryInputMode inputMode,
        @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal grossMonthly,
        @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal grossYearly,
        Boolean thirteenthSalary,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal ahvPct,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal alvPct,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal nbuPct,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal ktgPct,
        @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal pensionFixed,
        @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal otherFixed
) {}
