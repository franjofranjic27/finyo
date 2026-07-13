package ch.finyo.salary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Single source of the Swiss gross-to-net payslip calculation.
 *
 * <p>Every deduction line and every net amount is rounded to 5 centimes
 * ("Rundung auf 5 Rappen"), matching real Swiss payslips. Percentage
 * deductions (AHV, ALV, NBU, KTG) apply to the full yearly gross including
 * a 13th salary; fixed contributions (PENSION, OTHER) are charged 12 times
 * per year regardless of the 13th salary.
 *
 * <p>The authoritative gross depends on the input mode: MONTHLY multiplies
 * the entered monthly gross by 12 (13 with a 13th salary), while YEARLY
 * treats the entered yearly gross as the total annual amount — including the
 * 13th salary if enabled — and derives the monthly display value from it.
 */
final class SalaryCalculationModel {

    private static final BigDecimal TWENTY = new BigDecimal("20");
    private static final BigDecimal TWELVE = new BigDecimal("12");
    private static final BigDecimal THIRTEEN = new BigDecimal("13");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private SalaryCalculationModel() {
    }

    /** Yearly gross derived from the authoritative monthly gross (12 or 13 salaries). */
    static BigDecimal deriveGrossYearly(BigDecimal grossMonthly, boolean thirteenthSalary) {
        return grossMonthly.multiply(monthsPerYear(thirteenthSalary));
    }

    /** Monthly gross derived from the authoritative yearly gross, rounded to centimes. */
    static BigDecimal deriveGrossMonthly(BigDecimal grossYearly, boolean thirteenthSalary) {
        return grossYearly.divide(monthsPerYear(thirteenthSalary), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal monthsPerYear(boolean thirteenthSalary) {
        return thirteenthSalary ? THIRTEEN : TWELVE;
    }

    static SalaryResult calculate(SalaryProfile profile) {
        boolean thirteenthSalary = profile.isThirteenthSalary();
        BigDecimal grossMonthly;
        BigDecimal grossYearly;
        if (profile.getInputMode() == SalaryInputMode.YEARLY) {
            grossYearly = profile.getGrossYearly().setScale(2, RoundingMode.HALF_UP);
            grossMonthly = deriveGrossMonthly(grossYearly, thirteenthSalary);
        } else {
            grossMonthly = profile.getGrossMonthly();
            grossYearly = deriveGrossYearly(grossMonthly, thirteenthSalary).setScale(2, RoundingMode.HALF_UP);
        }

        List<SalaryDeduction> deductions = List.of(
                percentageLine(SalaryDeductionType.AHV, profile.getAhvPct(), grossMonthly, grossYearly),
                percentageLine(SalaryDeductionType.ALV, profile.getAlvPct(), grossMonthly, grossYearly),
                percentageLine(SalaryDeductionType.NBU, profile.getNbuPct(), grossMonthly, grossYearly),
                percentageLine(SalaryDeductionType.KTG, profile.getKtgPct(), grossMonthly, grossYearly),
                fixedLine(SalaryDeductionType.PENSION, profile.getPensionFixed()),
                fixedLine(SalaryDeductionType.OTHER, profile.getOtherFixed()));

        BigDecimal totalPerMonth = sum(deductions, SalaryDeduction::perMonth);
        BigDecimal totalPerYear = sum(deductions, SalaryDeduction::perYear);
        BigDecimal netMonthly = round5(grossMonthly.subtract(totalPerMonth));
        BigDecimal netYearly = round5(grossYearly.subtract(totalPerYear));
        BigDecimal totalDeductionsPct = totalDeductionsPct(totalPerMonth, grossMonthly);

        return new SalaryResult(grossMonthly, grossYearly, deductions,
                totalDeductionsPct, totalPerMonth, totalPerYear, netMonthly, netYearly);
    }

    private static SalaryDeduction percentageLine(SalaryDeductionType type, BigDecimal pct,
                                                  BigDecimal grossMonthly, BigDecimal grossYearly) {
        return new SalaryDeduction(type, pct,
                round5(grossMonthly.multiply(pct).movePointLeft(2)),
                round5(grossYearly.multiply(pct).movePointLeft(2)));
    }

    private static SalaryDeduction fixedLine(SalaryDeductionType type, BigDecimal amount) {
        return new SalaryDeduction(type, null,
                round5(amount),
                round5(amount.multiply(TWELVE)));
    }

    private static BigDecimal sum(List<SalaryDeduction> deductions,
                                  java.util.function.Function<SalaryDeduction, BigDecimal> line) {
        return deductions.stream()
                .map(line)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Total deductions as share of the gross monthly salary; 0.0 for a zero gross. */
    private static BigDecimal totalDeductionsPct(BigDecimal totalPerMonth, BigDecimal grossMonthly) {
        if (grossMonthly.signum() == 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return totalPerMonth.multiply(HUNDRED).divide(grossMonthly, 1, RoundingMode.HALF_UP);
    }

    /** Swiss payslip rounding to 5 centimes, e.g. 26.33085 → 26.35, 306.711 → 306.70. */
    private static BigDecimal round5(BigDecimal value) {
        return value.multiply(TWENTY)
                .setScale(0, RoundingMode.HALF_UP)
                .divide(TWENTY, 2, RoundingMode.HALF_UP);
    }
}
