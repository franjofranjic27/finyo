package ch.finyo.tax;

import ch.finyo.common.SwissTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Year;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.times;

/**
 * Pure unit tests for Pillar3CalculationService with a mocked TaxCalculationService.
 *
 * The projection maths is verified with hand-computable numbers (0% or 10%
 * return) so the assertions double as a specification:
 *   balance_n = (balance_{n-1} * (1 + r)) + contribution   (end-of-year deposit)
 *
 * Tax-saving behaviour: delta between a calculation without and with the 3a
 * deduction, clamped at zero, and silently zero when income/canton is missing
 * or the tax engine fails (the projection must still succeed).
 */
@ExtendWith(MockitoExtension.class)
class Pillar3CalculationServiceTest {

    private static final BigDecimal LEGAL_MAX_2025 = new BigDecimal("7258");

    @Mock
    private TaxCalculationService taxCalculationService;

    @InjectMocks
    private Pillar3CalculationService pillar3CalculationService;

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private Pillar3InputRequest projectionOnlyRequest(String balance, String contribution,
                                                      double returnPercent, int years) {
        return new Pillar3InputRequest(
                new BigDecimal(balance), new BigDecimal(contribution),
                returnPercent, years, null, null, null, 0);
    }

    private Pillar3InputRequest requestWithIncome(String contribution) {
        return new Pillar3InputRequest(
                BigDecimal.ZERO, new BigDecimal(contribution),
                0.0, 1, new BigDecimal("100000"), TaxCivilStatus.SINGLE, "SG", 2025);
    }

    /** Only totalIncomeTax matters for the pillar-3a saving delta. */
    private TaxResultResponse taxResultWithTotal(String totalIncomeTax) {
        return new TaxResultResponse(
                2025, "SG", null, TaxCivilStatus.SINGLE,
                null, null, null,
                null, null, null, null, new BigDecimal(totalIncomeTax),
                null, null, null, null, null, List.of());
    }

    // =========================================================================
    // Contribution status
    // =========================================================================

    @Test
    void contribution_below_the_legal_maximum_reports_the_remaining_headroom() {
        Pillar3ResultResponse result = pillar3CalculationService.calculate(
                projectionOnlyRequest("0", "5000", 0.0, 1));

        assertThat(result.isMaxContribution()).isFalse();
        assertThat(result.maxContribution()).isEqualByComparingTo(LEGAL_MAX_2025);
        assertThat(result.remainingUntilMax()).isEqualByComparingTo("2258");
    }

    @Test
    void contribution_at_the_legal_maximum_is_flagged_with_zero_remaining() {
        Pillar3ResultResponse result = pillar3CalculationService.calculate(
                projectionOnlyRequest("0", "7258", 0.0, 1));

        assertThat(result.isMaxContribution()).isTrue();
        assertThat(result.remainingUntilMax()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void contribution_above_the_legal_maximum_never_reports_negative_remaining() {
        Pillar3ResultResponse result = pillar3CalculationService.calculate(
                projectionOnlyRequest("0", "9000", 0.0, 1));

        assertThat(result.isMaxContribution()).isTrue();
        assertThat(result.remainingUntilMax()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // =========================================================================
    // Projection maths
    // =========================================================================

    @Test
    void projection_with_zero_return_accumulates_plain_contributions() {
        Pillar3ResultResponse result = pillar3CalculationService.calculate(
                projectionOnlyRequest("0", "1000", 0.0, 3));

        assertThat(result.projectedBalanceAtRetirement()).isEqualByComparingTo("3000");
        assertThat(result.totalContributionsOverPeriod()).isEqualByComparingTo("3000");
        assertThat(result.totalReturnsOverPeriod()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void projection_compounds_the_existing_balance_before_adding_the_yearly_contribution() {
        // Year 1: 1000 * 1.10 = 1100, + 500 = 1600
        // Year 2: 1600 * 1.10 = 1760, + 500 = 2260
        Pillar3ResultResponse result = pillar3CalculationService.calculate(
                projectionOnlyRequest("1000", "500", 10.0, 2));

        assertThat(result.projectedBalanceAtRetirement()).isEqualByComparingTo("2260.00");
        assertThat(result.totalContributionsOverPeriod()).isEqualByComparingTo("1000");
        assertThat(result.totalReturnsOverPeriod()).isEqualByComparingTo("260.00");
    }

    @Test
    void projection_contains_one_entry_per_year_labelled_with_the_calendar_year() {
        int currentYear = Year.now(SwissTime.ZONE).getValue();

        Pillar3ResultResponse result = pillar3CalculationService.calculate(
                projectionOnlyRequest("0", "1000", 0.0, 3));

        assertThat(result.yearlyProjection()).hasSize(3);
        assertThat(result.yearlyProjection().get(0).year()).isEqualTo(currentYear + 1);
        assertThat(result.yearlyProjection().get(2).year()).isEqualTo(currentYear + 3);
        assertThat(result.yearlyProjection().get(1).balance()).isEqualByComparingTo("2000");
    }

    @Test
    void payout_tax_is_3_5_percent_of_the_projected_balance() {
        Pillar3ResultResponse result = pillar3CalculationService.calculate(
                projectionOnlyRequest("0", "1000", 0.0, 10));

        assertThat(result.estimatedPayoutTax()).isEqualByComparingTo("350.00");
        assertThat(result.netValueAfterPayoutTax()).isEqualByComparingTo("9650.00");
    }

    @Test
    void equivalent_taxable_savings_taxes_contributions_at_25_percent_before_investing() {
        // 0% return → only the contribution tax applies: 2 * 1000 * 0.75 = 1500
        Pillar3ResultResponse result = pillar3CalculationService.calculate(
                projectionOnlyRequest("0", "1000", 0.0, 2));

        assertThat(result.equivalentTaxableSavings()).isEqualByComparingTo("1500.00");
    }

    // =========================================================================
    // Tax saving via TaxCalculationService
    // =========================================================================

    @Test
    void tax_saving_is_zero_and_tax_engine_untouched_when_income_or_canton_is_missing() {
        Pillar3ResultResponse result = pillar3CalculationService.calculate(
                projectionOnlyRequest("0", "5000", 0.0, 1));

        assertThat(result.annualTaxSaving()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.taxSavingIfMaxContribution()).isEqualByComparingTo(BigDecimal.ZERO);
        then(taxCalculationService).shouldHaveNoInteractions();
    }

    @Test
    void tax_saving_is_the_delta_between_calculations_without_and_with_the_contribution() {
        given(taxCalculationService.calculate(any(TaxInputRequest.class)))
                .willAnswer(invocation -> {
                    TaxInputRequest input = invocation.getArgument(0);
                    boolean withContribution = input.pillar3aContribution().compareTo(BigDecimal.ZERO) > 0;
                    return withContribution ? taxResultWithTotal("18000") : taxResultWithTotal("20000");
                });

        Pillar3ResultResponse result = pillar3CalculationService.calculate(requestWithIncome("7258"));

        assertThat(result.annualTaxSaving()).isEqualByComparingTo("2000");
    }

    @Test
    void tax_saving_at_max_contribution_reuses_the_annual_saving_without_extra_tax_calls() {
        given(taxCalculationService.calculate(any(TaxInputRequest.class)))
                .willReturn(taxResultWithTotal("20000"));

        Pillar3ResultResponse result = pillar3CalculationService.calculate(requestWithIncome("7258"));

        assertThat(result.taxSavingIfMaxContribution()).isEqualByComparingTo(result.annualTaxSaving());
        then(taxCalculationService).should(times(2)).calculate(any(TaxInputRequest.class));
    }

    @Test
    void tax_saving_if_max_runs_a_second_delta_with_the_legal_maximum_when_below_max() {
        given(taxCalculationService.calculate(any(TaxInputRequest.class)))
                .willAnswer(invocation -> {
                    TaxInputRequest input = invocation.getArgument(0);
                    if (input.pillar3aContribution().compareTo(LEGAL_MAX_2025) == 0) {
                        return taxResultWithTotal("17000");
                    }
                    if (input.pillar3aContribution().compareTo(BigDecimal.ZERO) > 0) {
                        return taxResultWithTotal("19000");
                    }
                    return taxResultWithTotal("20000");
                });

        Pillar3ResultResponse result = pillar3CalculationService.calculate(requestWithIncome("3000"));

        assertThat(result.annualTaxSaving()).isEqualByComparingTo("1000");
        assertThat(result.taxSavingIfMaxContribution()).isEqualByComparingTo("3000");
        then(taxCalculationService).should(times(4)).calculate(any(TaxInputRequest.class));
    }

    @Test
    void tax_saving_is_clamped_to_zero_when_the_delta_would_be_negative() {
        given(taxCalculationService.calculate(any(TaxInputRequest.class)))
                .willAnswer(invocation -> {
                    TaxInputRequest input = invocation.getArgument(0);
                    boolean withContribution = input.pillar3aContribution().compareTo(BigDecimal.ZERO) > 0;
                    // Pathological rate table: contribution appears to increase the tax
                    return withContribution ? taxResultWithTotal("21000") : taxResultWithTotal("20000");
                });

        Pillar3ResultResponse result = pillar3CalculationService.calculate(requestWithIncome("7258"));

        assertThat(result.annualTaxSaving()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void tax_engine_failure_defaults_the_saving_to_zero_but_keeps_the_projection() {
        given(taxCalculationService.calculate(any(TaxInputRequest.class)))
                .willThrow(new IllegalStateException("rate table missing"));

        Pillar3ResultResponse result = pillar3CalculationService.calculate(requestWithIncome("7258"));

        assertThat(result.annualTaxSaving()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.projectedBalanceAtRetirement()).isEqualByComparingTo("7258");
    }

    @Test
    void tax_saving_calculation_passes_the_requested_year_canton_and_civil_status() {
        given(taxCalculationService.calculate(any(TaxInputRequest.class)))
                .willReturn(taxResultWithTotal("20000"));

        pillar3CalculationService.calculate(requestWithIncome("7258"));

        then(taxCalculationService).should(times(2)).calculate(argThat(input ->
                input.taxYear() == 2025
                        && "SG".equals(input.cantonCode())
                        && input.civilStatus() == TaxCivilStatus.SINGLE
                        && new BigDecimal("100000").compareTo(input.grossEmploymentIncome()) == 0));
    }
}
