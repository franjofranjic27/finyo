package ch.finyo.salary;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SalaryRequest(
        @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal grossMonthly,
        Boolean thirteenthSalary,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal ahvPct,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal alvPct,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal nbuPct,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal ktgPct,
        @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal pensionFixed,
        @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal otherFixed
) {}
