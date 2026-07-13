package ch.finyo.salary;

import java.math.BigDecimal;

/**
 * Stored (or default) salary profile plus the computed gross-to-net result.
 * Both gross fields echo the normalized stored values: the one matching
 * {@code inputMode} is the entered amount, the other one is derived.
 */
public record SalaryResponse(
        SalaryInputMode inputMode,
        BigDecimal grossMonthly,
        BigDecimal grossYearly,
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
                profile.getInputMode(),
                profile.getGrossMonthly(),
                profile.getGrossYearly(),
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
