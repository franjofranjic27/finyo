package ch.finyo.salary;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for the gross-to-net payslip model.
 *
 * Focus areas:
 *   1. The reference payslip (gross 5787, standard rates) line by line.
 *   2. Swiss 5-centime rounding, including the exact-half case (x.x25 → .x5).
 *   3. 13th salary: percentage lines on 13 months, fixed lines still ×12.
 *   4. Zero gross: all lines zero, totalDeductionsPct 0.
 */
class SalaryCalculationModelTest {

    private SalaryProfile profile(String grossMonthly, boolean thirteenthSalary,
                                  String ahvPct, String alvPct, String nbuPct, String ktgPct,
                                  String pensionFixed, String otherFixed) {
        return SalaryProfile.builder()
                .userId("user-salary-model")
                .grossMonthly(new BigDecimal(grossMonthly))
                .thirteenthSalary(thirteenthSalary)
                .ahvPct(new BigDecimal(ahvPct))
                .alvPct(new BigDecimal(alvPct))
                .nbuPct(new BigDecimal(nbuPct))
                .ktgPct(new BigDecimal(ktgPct))
                .pensionFixed(new BigDecimal(pensionFixed))
                .otherFixed(new BigDecimal(otherFixed))
                .build();
    }

    private SalaryProfile referenceProfile(boolean thirteenthSalary) {
        return profile("5787", thirteenthSalary, "5.3", "1.1", "0.455", "0.17", "85.35", "11");
    }

    private SalaryDeduction line(SalaryResult result, SalaryDeductionType type) {
        return result.deductions().stream()
                .filter(deduction -> deduction.type() == type)
                .findFirst()
                .orElseThrow();
    }

    private void assertLine(SalaryResult result, SalaryDeductionType type,
                            String expectedPerMonth, String expectedPerYear) {
        SalaryDeduction deduction = line(result, type);
        assertThat(deduction.perMonth()).as("%s perMonth", type).isEqualByComparingTo(expectedPerMonth);
        assertThat(deduction.perYear()).as("%s perYear", type).isEqualByComparingTo(expectedPerYear);
    }

    @Test
    void reference_payslip_without_13th_salary_matches_line_by_line() {
        SalaryResult result = SalaryCalculationModel.calculate(referenceProfile(false));

        assertThat(result.grossMonthly()).isEqualByComparingTo("5787");
        assertThat(result.grossYearly()).isEqualByComparingTo("69444.00");

        assertLine(result, SalaryDeductionType.AHV, "306.70", "3680.55");
        assertLine(result, SalaryDeductionType.ALV, "63.65", "763.90");
        assertLine(result, SalaryDeductionType.NBU, "26.35", "315.95");
        assertLine(result, SalaryDeductionType.KTG, "9.85", "118.05");
        assertLine(result, SalaryDeductionType.PENSION, "85.35", "1024.20");
        assertLine(result, SalaryDeductionType.OTHER, "11.00", "132.00");

        assertThat(result.totalPerMonth()).isEqualByComparingTo("502.90");
        assertThat(result.totalPerYear()).isEqualByComparingTo("6034.65");
        assertThat(result.netMonthly()).isEqualByComparingTo("5284.10");
        // netYearly = grossYearly - totalPerYear = 69444.00 - 6034.65
        assertThat(result.netYearly()).isEqualByComparingTo("63409.35");
        assertThat(result.totalDeductionsPct()).isEqualByComparingTo("8.7");
    }

    @Test
    void deductions_are_ordered_ahv_alv_nbu_ktg_pension_other_and_pct_is_null_for_fixed_lines() {
        SalaryResult result = SalaryCalculationModel.calculate(referenceProfile(false));

        assertThat(result.deductions())
                .extracting(SalaryDeduction::type)
                .containsExactly(SalaryDeductionType.AHV, SalaryDeductionType.ALV,
                        SalaryDeductionType.NBU, SalaryDeductionType.KTG,
                        SalaryDeductionType.PENSION, SalaryDeductionType.OTHER);

        assertThat(line(result, SalaryDeductionType.AHV).pct()).isEqualByComparingTo("5.3");
        assertThat(line(result, SalaryDeductionType.KTG).pct()).isEqualByComparingTo("0.17");
        assertThat(line(result, SalaryDeductionType.PENSION).pct()).isNull();
        assertThat(line(result, SalaryDeductionType.OTHER).pct()).isNull();
    }

    @Test
    void thirteenth_salary_applies_percentage_lines_to_13_months_but_fixed_lines_to_12() {
        SalaryResult result = SalaryCalculationModel.calculate(referenceProfile(true));

        assertThat(result.grossYearly()).isEqualByComparingTo("75231.00");

        // monthly view is unchanged by the 13th salary
        assertThat(result.totalPerMonth()).isEqualByComparingTo("502.90");
        assertThat(result.netMonthly()).isEqualByComparingTo("5284.10");

        assertLine(result, SalaryDeductionType.AHV, "306.70", "3987.25");
        assertLine(result, SalaryDeductionType.ALV, "63.65", "827.55");
        assertLine(result, SalaryDeductionType.NBU, "26.35", "342.30");
        assertLine(result, SalaryDeductionType.KTG, "9.85", "127.90");
        // fixed contributions are charged 12x, not on the 13th salary
        assertLine(result, SalaryDeductionType.PENSION, "85.35", "1024.20");
        assertLine(result, SalaryDeductionType.OTHER, "11.00", "132.00");

        assertThat(result.totalPerYear()).isEqualByComparingTo("6441.20");
        assertThat(result.netYearly()).isEqualByComparingTo("68789.80");
    }

    @Test
    void rounds_to_5_centimes_with_half_up_at_the_exact_midpoint() {
        // 1000 x 0.1025% = 1.025 -> exactly between 1.00 and 1.05 -> HALF_UP -> 1.05
        SalaryResult midpoint = SalaryCalculationModel.calculate(
                profile("1000", false, "0.1025", "0", "0", "0", "0", "0"));
        assertThat(line(midpoint, SalaryDeductionType.AHV).perMonth()).isEqualByComparingTo("1.05");

        // 1000 x 0.102% = 1.02 -> below the midpoint -> rounds down to 1.00
        SalaryResult belowMidpoint = SalaryCalculationModel.calculate(
                profile("1000", false, "0.102", "0", "0", "0", "0", "0"));
        assertThat(line(belowMidpoint, SalaryDeductionType.AHV).perMonth()).isEqualByComparingTo("1.00");
    }

    @Test
    void zero_gross_yields_all_zero_lines_and_zero_total_pct() {
        SalaryResult result = SalaryCalculationModel.calculate(
                profile("0", false, "5.3", "1.1", "0.455", "0.17", "0", "0"));

        assertThat(result.grossYearly()).isEqualByComparingTo("0");
        assertThat(result.deductions()).hasSize(6)
                .allSatisfy(deduction -> {
                    assertThat(deduction.perMonth()).isEqualByComparingTo("0");
                    assertThat(deduction.perYear()).isEqualByComparingTo("0");
                });
        assertThat(result.totalPerMonth()).isEqualByComparingTo("0");
        assertThat(result.totalPerYear()).isEqualByComparingTo("0");
        assertThat(result.netMonthly()).isEqualByComparingTo("0");
        assertThat(result.netYearly()).isEqualByComparingTo("0");
        assertThat(result.totalDeductionsPct()).isEqualByComparingTo("0");
    }
}
