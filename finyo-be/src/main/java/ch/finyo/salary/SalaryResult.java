package ch.finyo.salary;

import java.math.BigDecimal;
import java.util.List;

/** Computed gross-to-net result; all amounts are rounded to 5 centimes. */
public record SalaryResult(
        BigDecimal grossMonthly,
        BigDecimal grossYearly,
        List<SalaryDeduction> deductions,
        BigDecimal totalDeductionsPct,
        BigDecimal totalPerMonth,
        BigDecimal totalPerYear,
        BigDecimal netMonthly,
        BigDecimal netYearly
) {}
