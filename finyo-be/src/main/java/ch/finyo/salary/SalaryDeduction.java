package ch.finyo.salary;

import java.math.BigDecimal;

/**
 * One deduction line of the computed payslip.
 *
 * @param pct configured rate for percentage-based types, {@code null} for
 *            fixed-amount types (PENSION, OTHER)
 */
public record SalaryDeduction(
        SalaryDeductionType type,
        BigDecimal pct,
        BigDecimal perMonth,
        BigDecimal perYear
) {}
