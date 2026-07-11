package ch.finyo.salary;

import java.math.BigDecimal;

/** Stored (or default) salary profile plus the computed gross-to-net result. */
public record SalaryResponse(
        BigDecimal grossMonthly,
        boolean thirteenthSalary,
        BigDecimal ahvPct,
        BigDecimal alvPct,
        BigDecimal nbuPct,
        BigDecimal ktgPct,
        BigDecimal pensionFixed,
        BigDecimal otherFixed,
        SalaryResult result
) {
    public static SalaryResponse from(SalaryProfile profile) {
        return new SalaryResponse(
                profile.getGrossMonthly(),
                profile.isThirteenthSalary(),
                profile.getAhvPct(),
                profile.getAlvPct(),
                profile.getNbuPct(),
                profile.getKtgPct(),
                profile.getPensionFixed(),
                profile.getOtherFixed(),
                SalaryCalculationModel.calculate(profile));
    }
}
