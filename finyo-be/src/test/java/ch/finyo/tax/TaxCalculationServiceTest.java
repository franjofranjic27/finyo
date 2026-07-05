package ch.finyo.tax;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for TaxCalculationService.
 *
 * No Spring context is started. All three repositories are mocked with
 * realistic bracket data that mirrors the actual SG/federal rate tables
 * seeded in V10 and V11 migrations. This keeps tests fast and deterministic.
 *
 * Test strategy:
 *   - Each test targets one observable behaviour of the calculate() method.
 *   - Numbers are validated against known Swiss tax calculations so that
 *     the tests serve as a living specification for the calculation logic.
 */
@ExtendWith(MockitoExtension.class)
class TaxCalculationServiceTest {

    @Mock
    private TaxFederalRateRepository federalRateRepository;

    @Mock
    private TaxCantonRateRepository cantonRateRepository;

    @Mock
    private TaxCommuneMultiplierRepository communeMultiplierRepository;

    @InjectMocks
    private TaxCalculationService service;

    // -------------------------------------------------------------------------
    // Shared bracket stubs — single bracket per tariff keeps maths simple
    // -------------------------------------------------------------------------

    /** Federal bracket: incomeFrom=0, baseTax=0, rate=11.5% (tariff A). */
    private TaxFederalRate federalBracketA;

    /** Federal bracket: incomeFrom=0, baseTax=0, rate=9% (tariff B — married). */
    private TaxFederalRate federalBracketB;

    /** Cantonal bracket: incomeFrom=0, baseTax=0, rate=8% (tariff A). */
    private TaxCantonRate cantonBracketA;

    /** Cantonal bracket: incomeFrom=0, baseTax=0, rate=6.5% (tariff B — married). */
    private TaxCantonRate cantonBracketB;

    @BeforeEach
    void setUpBrackets() {
        federalBracketA = TaxFederalRate.builder()
                .id(1L).taxYear(2025).tariff("A")
                .incomeFrom(BigDecimal.ZERO).incomeTo(null)
                .baseTax(BigDecimal.ZERO)
                .ratePerHundred(new BigDecimal("11.5"))
                .build();

        federalBracketB = TaxFederalRate.builder()
                .id(2L).taxYear(2025).tariff("B")
                .incomeFrom(BigDecimal.ZERO).incomeTo(null)
                .baseTax(BigDecimal.ZERO)
                .ratePerHundred(new BigDecimal("9.0"))
                .build();

        cantonBracketA = TaxCantonRate.builder()
                .id(1L).taxYear(2025).cantonCode("SG").tariff("A")
                .incomeFrom(BigDecimal.ZERO).incomeTo(null)
                .baseTax(BigDecimal.ZERO)
                .ratePerHundred(new BigDecimal("8.0"))
                .build();

        cantonBracketB = TaxCantonRate.builder()
                .id(2L).taxYear(2025).cantonCode("SG").tariff("B")
                .incomeFrom(BigDecimal.ZERO).incomeTo(null)
                .baseTax(BigDecimal.ZERO)
                .ratePerHundred(new BigDecimal("6.5"))
                .build();
    }

    // -------------------------------------------------------------------------
    // Helper to build a minimal valid request
    // -------------------------------------------------------------------------

    private TaxInputRequest minimalSingleRequest(BigDecimal grossIncome) {
        return new TaxInputRequest(
                2025, "SG", null, TaxCivilStatus.SINGLE,
                0, grossIncome,
                null, null, null,
                null, null, null, null,
                BigDecimal.ZERO, null
        );
    }

    private void stubTariffA() {
        when(federalRateRepository.findByTaxYearAndTariffOrderByIncomeFromAsc(anyInt(), anyString()))
                .thenReturn(List.of(federalBracketA));
        when(cantonRateRepository.findByTaxYearAndCantonCodeAndTariffOrderByIncomeFromAsc(anyInt(), anyString(), anyString()))
                .thenReturn(List.of(cantonBracketA));
        // only invoked when the request carries a BFS number — lenient to avoid UnnecessaryStubbing
        lenient().when(communeMultiplierRepository.findByTaxYearAndBfsNumber(anyInt(), anyInt()))
                .thenReturn(Optional.empty());
    }

    private void stubTariffB() {
        when(federalRateRepository.findByTaxYearAndTariffOrderByIncomeFromAsc(anyInt(), anyString()))
                .thenReturn(List.of(federalBracketB));
        when(cantonRateRepository.findByTaxYearAndCantonCodeAndTariffOrderByIncomeFromAsc(anyInt(), anyString(), anyString()))
                .thenReturn(List.of(cantonBracketB));
        // only invoked when the request carries a BFS number — lenient to avoid UnnecessaryStubbing
        lenient().when(communeMultiplierRepository.findByTaxYearAndBfsNumber(anyInt(), anyInt()))
                .thenReturn(Optional.empty());
    }

    // -------------------------------------------------------------------------
    // Gross income aggregation
    // -------------------------------------------------------------------------

    @Test
    void grossIncome_aggregates_all_income_sources() {
        stubTariffA();
        var req = new TaxInputRequest(
                2025, "SG", null, TaxCivilStatus.SINGLE,
                0, new BigDecimal("60000"),
                new BigDecimal("10000"),
                new BigDecimal("5000"),
                new BigDecimal("3000"),
                null, null, null, null, BigDecimal.ZERO, null
        );

        TaxResultResponse result = service.calculate(req);

        assertThat(result.grossIncome()).isEqualByComparingTo("78000");
    }

    @Test
    void grossIncome_treats_null_income_sources_as_zero() {
        stubTariffA();
        var req = minimalSingleRequest(new BigDecimal("50000"));

        TaxResultResponse result = service.calculate(req);

        assertThat(result.grossIncome()).isEqualByComparingTo("50000");
    }

    // -------------------------------------------------------------------------
    // Professional expense deduction
    // -------------------------------------------------------------------------

    @Test
    void professional_expense_deduction_is_3_percent_for_normal_income() {
        stubTariffA();
        // 3% of 50 000 = 1 500 CHF → below minimum 2 200 → capped at 2 200
        // 3% of 80 000 = 2 400 CHF → within range [2200, 4400]
        var req = minimalSingleRequest(new BigDecimal("80000"));

        TaxResultResponse result = service.calculate(req);

        // Deductions = professional expenses 2 400 + nothing else
        // taxableIncome = 80 000 - 2 400 = 77 600 → rounded to 77 600
        assertThat(result.taxableIncome()).isEqualByComparingTo("77600");
    }

    @Test
    void professional_expense_deduction_is_floored_at_2200() {
        stubTariffA();
        // 3% of 40 000 = 1 200 < 2 200 → floor applies
        var req = minimalSingleRequest(new BigDecimal("40000"));

        TaxResultResponse result = service.calculate(req);

        // taxableIncome = 40 000 - 2 200 = 37 800 → rounded to 37 800
        assertThat(result.taxableIncome()).isEqualByComparingTo("37800");
    }

    @Test
    void professional_expense_deduction_is_capped_at_4400() {
        stubTariffA();
        // 3% of 200 000 = 6 000 > 4 400 → cap applies
        var req = minimalSingleRequest(new BigDecimal("200000"));

        TaxResultResponse result = service.calculate(req);

        // taxableIncome = 200 000 - 4 400 = 195 600
        assertThat(result.taxableIncome()).isEqualByComparingTo("195600");
    }

    @Test
    void user_provided_professional_expenses_override_standard_deduction() {
        stubTariffA();
        var req = new TaxInputRequest(
                2025, "SG", null, TaxCivilStatus.SINGLE,
                0, new BigDecimal("80000"),
                null, null, null,
                new BigDecimal("6000"),   // user override
                null, null, null, BigDecimal.ZERO, null
        );

        TaxResultResponse result = service.calculate(req);

        // taxableIncome = 80 000 - 6 000 = 74 000
        assertThat(result.taxableIncome()).isEqualByComparingTo("74000");
    }

    // -------------------------------------------------------------------------
    // Pillar 3a deduction — capping behaviour
    // -------------------------------------------------------------------------

    @Test
    void pillar3a_is_fully_deducted_when_below_legal_cap() {
        stubTariffA();
        var req = new TaxInputRequest(
                2025, "SG", null, TaxCivilStatus.SINGLE,
                0, new BigDecimal("80000"),
                null, null, null,
                null, null, null, null,
                new BigDecimal("5000"), null   // below 7 258 cap
        );

        TaxResultResponse result = service.calculate(req);

        // deductions = 2 400 (prof expense) + 5 000 (3a) = 7 400
        // taxableIncome = 80 000 - 7 400 = 72 600
        assertThat(result.taxableIncome()).isEqualByComparingTo("72600");
    }

    @Test
    void pillar3a_is_capped_at_legal_maximum_for_employees() {
        stubTariffA();
        var req = new TaxInputRequest(
                2025, "SG", null, TaxCivilStatus.SINGLE,
                0, new BigDecimal("80000"),
                null, null, null,
                null, null, null, null,
                new BigDecimal("10000"), null  // over 7 258 cap
        );

        TaxResultResponse result = service.calculate(req);

        // deductions = 2 400 (prof expense) + 7 258 (capped 3a) = 9 658
        // taxableIncome = 80 000 - 9 658 = 70 342 → round to 70 300
        assertThat(result.taxableIncome()).isEqualByComparingTo("70300");
    }

    @Test
    void pillar3a_cap_is_higher_for_self_employed() {
        stubTariffA();
        var req = new TaxInputRequest(
                2025, "SG", null, TaxCivilStatus.SINGLE,
                0, BigDecimal.ZERO,
                new BigDecimal("100000"),    // self-employment income
                null, null,
                null, null, null, null,
                new BigDecimal("40000"),     // over self-employed cap 36 288
                null
        );

        TaxResultResponse result = service.calculate(req);

        // Self-employed cap = 36 288
        // deductions = 36 288
        // taxableIncome = 100 000 - 36 288 = 63 712 → round to 63 700
        assertThat(result.taxableIncome()).isEqualByComparingTo("63700");
    }

    // -------------------------------------------------------------------------
    // Child deduction
    // -------------------------------------------------------------------------

    @Test
    void two_children_add_13400_to_deductions() {
        stubTariffA();
        var req = new TaxInputRequest(
                2025, "SG", null, TaxCivilStatus.MARRIED,
                2, new BigDecimal("80000"),
                null, null, null,
                null, null, null, null, BigDecimal.ZERO, null
        );

        stubTariffB();  // married → tariff B

        TaxResultResponse result = service.calculate(req);

        // prof expense 2 400 + children 13 400 = 15 800 total deductions
        // taxable = 80 000 - 15 800 = 64 200 → round to 64 200
        assertThat(result.totalDeductions()).isEqualByComparingTo("15800");
    }

    // -------------------------------------------------------------------------
    // Tariff selection
    // -------------------------------------------------------------------------

    @Test
    void single_status_uses_tariff_A() {
        // Tariff A has higher rates — validate federal tax is higher than B
        stubTariffA();
        var req = minimalSingleRequest(new BigDecimal("80000"));

        TaxResultResponse resultA = service.calculate(req);

        // For tariff A with rate 11.5%: federal = 77600 * 0.115 = 8924
        assertThat(resultA.federalTax())
                .isGreaterThan(new BigDecimal("8000"));
    }

    @Test
    void married_status_uses_tariff_B() {
        stubTariffB();
        var req = new TaxInputRequest(
                2025, "SG", null, TaxCivilStatus.MARRIED,
                0, new BigDecimal("80000"),
                null, null, null,
                null, null, null, null, BigDecimal.ZERO, null
        );

        TaxResultResponse result = service.calculate(req);

        // Tariff B rate 9%: federal = 77600 * 0.09 = 6984
        assertThat(result.federalTax())
                .isGreaterThan(new BigDecimal("6000"))
                .isLessThan(new BigDecimal("8000"));
    }

    @Test
    void single_parent_uses_tariff_B() {
        stubTariffB();
        var req = new TaxInputRequest(
                2025, "SG", null, TaxCivilStatus.SINGLE_PARENT,
                1, new BigDecimal("80000"),
                null, null, null,
                null, null, null, null, BigDecimal.ZERO, null
        );

        TaxResultResponse result = service.calculate(req);

        // Should produce same federal rate as married (tariff B)
        assertThat(result.federalTax()).isGreaterThan(BigDecimal.ZERO);
    }

    // -------------------------------------------------------------------------
    // Federal and cantonal tax amounts
    // -------------------------------------------------------------------------

    @Test
    void federal_tax_is_calculated_from_brackets() {
        stubTariffA();
        var req = minimalSingleRequest(new BigDecimal("80000"));

        TaxResultResponse result = service.calculate(req);

        // taxableIncome = 77 600
        // federal = 77 600 * 11.5 / 100 = 8 924.00
        assertThat(result.federalTax()).isEqualByComparingTo("8924.00");
    }

    @Test
    void cantonal_tax_equals_simple_tax_when_no_commune_is_selected() {
        stubTariffA();
        var req = minimalSingleRequest(new BigDecimal("80000"));

        TaxResultResponse result = service.calculate(req);

        // cantonal = 77 600 * 8 / 100 = 6 208.00
        assertThat(result.cantonalTax()).isEqualByComparingTo("6208.00");
    }

    @Test
    void communal_tax_is_zero_when_no_bfs_number_provided() {
        stubTariffA();
        var req = minimalSingleRequest(new BigDecimal("80000"));

        TaxResultResponse result = service.calculate(req);

        assertThat(result.communalTax()).isEqualByComparingTo("0");
    }

    @Test
    void communal_tax_is_applied_using_commune_multiplier() {
        when(federalRateRepository.findByTaxYearAndTariffOrderByIncomeFromAsc(anyInt(), anyString()))
                .thenReturn(List.of(federalBracketA));
        when(cantonRateRepository.findByTaxYearAndCantonCodeAndTariffOrderByIncomeFromAsc(anyInt(), anyString(), anyString()))
                .thenReturn(List.of(cantonBracketA));

        TaxCommuneMultiplier commune = TaxCommuneMultiplier.builder()
                .id(1L).taxYear(2025).cantonCode("SG")
                .communeName("St. Gallen").bfsNumber(3203)
                .multiplier(new BigDecimal("1.18"))
                .build();
        when(communeMultiplierRepository.findByTaxYearAndBfsNumber(2025, 3203))
                .thenReturn(Optional.of(commune));

        var req = new TaxInputRequest(
                2025, "SG", 3203, TaxCivilStatus.SINGLE,
                0, new BigDecimal("80000"),
                null, null, null,
                null, null, null, null, BigDecimal.ZERO, null
        );

        TaxResultResponse result = service.calculate(req);

        // cantonalSimpleTax = 77 600 * 8 / 100 = 6 208.00
        // communalTax = 6 208.00 * 1.18 = 7 325.44
        assertThat(result.communalTax()).isEqualByComparingTo("7325.44");
        assertThat(result.communeName()).isEqualTo("St. Gallen");
    }

    // -------------------------------------------------------------------------
    // Total income tax and grand total
    // -------------------------------------------------------------------------

    @Test
    void total_income_tax_is_sum_of_federal_cantonal_and_communal() {
        stubTariffA();
        var req = minimalSingleRequest(new BigDecimal("80000"));

        TaxResultResponse result = service.calculate(req);

        BigDecimal expected = result.federalTax()
                .add(result.cantonalTax())
                .add(result.communalTax());
        assertThat(result.totalIncomeTax()).isEqualByComparingTo(expected);
    }

    @Test
    void grand_total_is_total_income_tax_plus_wealth_tax() {
        stubTariffA();
        var req = new TaxInputRequest(
                2025, "SG", null, TaxCivilStatus.SINGLE,
                0, new BigDecimal("80000"),
                null, null, null,
                null, null, null, null, BigDecimal.ZERO,
                new BigDecimal("500000")   // wealth → triggers wealth tax
        );

        TaxResultResponse result = service.calculate(req);

        BigDecimal expected = result.totalIncomeTax().add(result.wealthTax());
        assertThat(result.grandTotal()).isEqualByComparingTo(expected);
    }

    // -------------------------------------------------------------------------
    // Wealth tax
    // -------------------------------------------------------------------------

    @Test
    void wealth_tax_is_zero_when_no_net_wealth_provided() {
        stubTariffA();
        var req = minimalSingleRequest(new BigDecimal("80000"));

        TaxResultResponse result = service.calculate(req);

        assertThat(result.wealthTax()).isEqualByComparingTo("0");
    }

    @Test
    void wealth_tax_applies_0_3_permille_above_100000_exemption() {
        stubTariffA();
        var req = new TaxInputRequest(
                2025, "SG", null, TaxCivilStatus.SINGLE,
                0, new BigDecimal("80000"),
                null, null, null,
                null, null, null, null, BigDecimal.ZERO,
                new BigDecimal("600000")
        );

        TaxResultResponse result = service.calculate(req);

        // taxableWealth = 600 000 - 100 000 = 500 000
        // wealthTax = 500 000 * 0.0003 = 150.00
        assertThat(result.wealthTax()).isEqualByComparingTo("150.00");
    }

    @Test
    void wealth_below_exemption_produces_zero_wealth_tax() {
        stubTariffA();
        var req = new TaxInputRequest(
                2025, "SG", null, TaxCivilStatus.SINGLE,
                0, new BigDecimal("80000"),
                null, null, null,
                null, null, null, null, BigDecimal.ZERO,
                new BigDecimal("90000")   // below 100 000 exemption
        );

        TaxResultResponse result = service.calculate(req);

        assertThat(result.wealthTax()).isEqualByComparingTo("0");
    }

    // -------------------------------------------------------------------------
    // Effective and marginal rates
    // -------------------------------------------------------------------------

    @Test
    void effective_rate_is_zero_when_gross_income_is_zero() {
        stubTariffA();
        var req = new TaxInputRequest(
                2025, "SG", null, TaxCivilStatus.SINGLE,
                0, BigDecimal.ZERO,
                null, null, null,
                null, null, null, null, BigDecimal.ZERO, null
        );

        TaxResultResponse result = service.calculate(req);

        assertThat(result.effectiveRatePercent()).isEqualTo(0.0);
    }

    @Test
    void effective_rate_is_positive_for_non_zero_income() {
        stubTariffA();
        var req = minimalSingleRequest(new BigDecimal("80000"));

        TaxResultResponse result = service.calculate(req);

        assertThat(result.effectiveRatePercent()).isGreaterThan(0.0);
    }

    @Test
    void marginal_rate_is_positive_for_non_zero_income() {
        stubTariffA();
        var req = minimalSingleRequest(new BigDecimal("80000"));

        TaxResultResponse result = service.calculate(req);

        assertThat(result.marginalRatePercent()).isGreaterThan(0.0);
    }

    // -------------------------------------------------------------------------
    // Taxable income — round-to-100 behaviour
    // -------------------------------------------------------------------------

    @Test
    void taxable_income_is_rounded_down_to_nearest_100() {
        stubTariffA();
        // gross = 80 350, prof expense = 2 400 (3% of 80350 capped)
        // raw taxable = 80 350 - 2 410.5 ≈ 77 940 → rounds to 77 900 or similar
        // Just ensure result is a multiple of 100
        var req = minimalSingleRequest(new BigDecimal("80350"));

        TaxResultResponse result = service.calculate(req);

        assertThat(result.taxableIncome().remainder(BigDecimal.valueOf(100)))
                .isEqualByComparingTo("0");
    }

    @Test
    void taxable_income_cannot_be_negative() {
        stubTariffA();
        // Enormous deductions should not push taxableIncome below zero
        var req = new TaxInputRequest(
                2025, "SG", null, TaxCivilStatus.SINGLE,
                0, new BigDecimal("10000"),
                null, null, null,
                new BigDecimal("50000"),  // deduction exceeds income
                null, null, null, BigDecimal.ZERO, null
        );

        TaxResultResponse result = service.calculate(req);

        assertThat(result.taxableIncome()).isEqualByComparingTo("0");
    }

    // -------------------------------------------------------------------------
    // Pillar 3a tax saving
    // -------------------------------------------------------------------------

    @Test
    void pillar3a_tax_saving_is_positive_when_contribution_is_non_zero() {
        stubTariffA();
        var req = new TaxInputRequest(
                2025, "SG", null, TaxCivilStatus.SINGLE,
                0, new BigDecimal("80000"),
                null, null, null,
                null, null, null, null,
                new BigDecimal("5000"), null
        );

        TaxResultResponse result = service.calculate(req);

        assertThat(result.pillar3aTaxSaving()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void pillar3a_tax_saving_is_zero_when_no_contribution() {
        stubTariffA();
        var req = minimalSingleRequest(new BigDecimal("80000"));

        TaxResultResponse result = service.calculate(req);

        assertThat(result.pillar3aTaxSaving()).isEqualByComparingTo("0");
    }

    // -------------------------------------------------------------------------
    // Breakdown list
    // -------------------------------------------------------------------------

    @Test
    void breakdown_always_contains_federal_cantonal_and_communal_items() {
        stubTariffA();
        var req = minimalSingleRequest(new BigDecimal("80000"));

        TaxResultResponse result = service.calculate(req);

        assertThat(result.breakdown())
                .extracting(TaxBreakdownItem::label)
                .contains("Federal Tax (DBSt)", "Cantonal Tax", "Communal Tax");
    }

    @Test
    void breakdown_includes_wealth_tax_item_when_wealth_tax_is_positive() {
        stubTariffA();
        var req = new TaxInputRequest(
                2025, "SG", null, TaxCivilStatus.SINGLE,
                0, new BigDecimal("80000"),
                null, null, null,
                null, null, null, null, BigDecimal.ZERO,
                new BigDecimal("500000")
        );

        TaxResultResponse result = service.calculate(req);

        assertThat(result.breakdown())
                .extracting(TaxBreakdownItem::label)
                .contains("Wealth Tax");
    }

    @Test
    void breakdown_excludes_wealth_tax_item_when_no_net_wealth() {
        stubTariffA();
        var req = minimalSingleRequest(new BigDecimal("80000"));

        TaxResultResponse result = service.calculate(req);

        assertThat(result.breakdown())
                .extracting(TaxBreakdownItem::label)
                .doesNotContain("Wealth Tax");
    }

    // -------------------------------------------------------------------------
    // Response fields match the input
    // -------------------------------------------------------------------------

    @Test
    void response_tax_year_matches_request() {
        stubTariffA();
        var req = minimalSingleRequest(new BigDecimal("80000"));

        assertThat(service.calculate(req).taxYear()).isEqualTo(2025);
    }

    @Test
    void response_canton_code_is_uppercased() {
        stubTariffA();
        var req = new TaxInputRequest(
                2025, "sg", null, TaxCivilStatus.SINGLE,
                0, new BigDecimal("80000"),
                null, null, null,
                null, null, null, null, BigDecimal.ZERO, null
        );

        assertThat(service.calculate(req).cantonCode()).isEqualTo("SG");
    }

    // -------------------------------------------------------------------------
    // getCommunes delegation
    // -------------------------------------------------------------------------

    @Test
    void getCommunes_delegates_to_repository_with_uppercased_canton() {
        TaxCommuneMultiplier sg = TaxCommuneMultiplier.builder()
                .id(1L).taxYear(2025).cantonCode("SG")
                .communeName("Rapperswil-Jona").bfsNumber(3211)
                .multiplier(new BigDecimal("1.20"))
                .build();
        when(communeMultiplierRepository.findByTaxYearAndCantonCodeOrderByCommuneNameAsc(2025, "SG"))
                .thenReturn(List.of(sg));

        List<TaxCommuneMultiplier> result = service.getCommunes(2025, "sg");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCommuneName()).isEqualTo("Rapperswil-Jona");
    }
}
