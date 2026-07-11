package ch.finyo.pillar3;

import ch.finyo.common.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * Pure unit tests for Pillar3CompareService with a mocked repository.
 *
 * The projection maths is verified with hand-computable numbers so the
 * assertions double as a specification. Yearly loop (i = 1..years):
 *
 *   balance   = round2(balance × (1 + grossRate))     grossRate = 0.01 + equityPct/100 × 0.05
 *   fee       = round2(balance × terPct/100)          TER charged on the grown balance
 *   balance   = balance − fee + annualContribution    contribution deposited at year end
 *
 * finalCapital = balance, totalPaidIn = currentBalance + contribution × years.
 */
@ExtendWith(MockitoExtension.class)
class Pillar3CompareServiceTest {

    @Mock
    private Pillar3ProductRepository productRepository;

    @InjectMocks
    private Pillar3CompareService compareService;

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private Pillar3Product product(UUID id, String name, String equityPct, String terPct, boolean active) {
        return Pillar3Product.builder()
                .id(id)
                .provider("Test Provider")
                .name(name)
                .isin("CH0000000000")
                .valor("1234567")
                .equityPct(new BigDecimal(equityPct))
                .terPct(new BigDecimal(terPct))
                .active(active)
                .sortOrder(0)
                .build();
    }

    private Pillar3CompareRequest request(String currentBalance, String annualContribution,
                                          int years, List<UUID> productIds) {
        return new Pillar3CompareRequest(
                new BigDecimal(currentBalance), new BigDecimal(annualContribution), years, productIds);
    }

    private Pillar3ProductComparison compareSingle(Pillar3Product product,
                                                   String balance, String contribution, int years) {
        given(productRepository.findAllById(List.of(product.getId()))).willReturn(List.of(product));

        Pillar3CompareResponse response = compareService.compare(
                request(balance, contribution, years, List.of(product.getId())));

        return response.products().get(0);
    }

    // =========================================================================
    // Return formula: grossRate = 0.01 + equityPct/100 × 0.05
    // =========================================================================

    @Test
    void full_equity_product_yields_6_percent_gross_return() {
        Pillar3Product fund = product(UUID.randomUUID(), "Equity 100", "100", "0.000", true);

        // 1000 × 1.06 = 1060.00, no TER, no contribution
        Pillar3ProductComparison result = compareSingle(fund, "1000", "0", 1);

        assertThat(result.avgReturnPct()).isEqualByComparingTo("6.00");
        assertThat(result.finalCapital()).isEqualByComparingTo("1060.00");
    }

    @Test
    void zero_equity_product_yields_the_1_percent_base_return() {
        Pillar3Product fund = product(UUID.randomUUID(), "Cash", "0", "0.000", true);

        // 1000 × 1.01 = 1010.00
        Pillar3ProductComparison result = compareSingle(fund, "1000", "0", 1);

        assertThat(result.avgReturnPct()).isEqualByComparingTo("1.00");
        assertThat(result.finalCapital()).isEqualByComparingTo("1010.00");
    }

    // =========================================================================
    // Projection maths (boundary years=1 and fee accumulation)
    // =========================================================================

    @Test
    void single_year_projection_grows_the_balance_then_deducts_the_ter_fee() {
        Pillar3Product fund = product(UUID.randomUUID(), "Equity 100", "100", "0.50", true);

        // Year 1: 1000 × 1.06 = 1060.00
        //         fee = 1060.00 × 0.005 = 5.30
        //         balance = 1060.00 − 5.30 + 0 = 1054.70
        Pillar3ProductComparison result = compareSingle(fund, "1000", "0", 1);

        assertThat(result.finalCapital()).isEqualByComparingTo("1054.70");
        assertThat(result.totalFees()).isEqualByComparingTo("5.30");
        assertThat(result.netReturnPct()).isEqualByComparingTo("5.50");
    }

    @Test
    void fees_accumulate_on_the_grown_balance_each_year() {
        Pillar3Product fund = product(UUID.randomUUID(), "Expensive Cash", "0", "1.000", true);

        // Year 1: 1000.00 × 1.01 = 1010.00, fee 10.10, balance 999.90
        // Year 2: 999.90 × 1.01 = 1009.90 (HALF_UP), fee 10.10, balance 999.80
        Pillar3ProductComparison result = compareSingle(fund, "1000", "0", 2);

        assertThat(result.totalFees()).isEqualByComparingTo("20.20");
        assertThat(result.finalCapital()).isEqualByComparingTo("999.80");
    }

    @Test
    void contribution_is_deposited_at_year_end_after_growth_and_fee() {
        Pillar3Product fund = product(UUID.randomUUID(), "Cash", "0", "0.000", true);

        // Year 1: 0 × 1.01 = 0.00, + 1000 = 1000.00 (first contribution earns nothing)
        // Year 2: 1000.00 × 1.01 = 1010.00, + 1000 = 2010.00
        Pillar3ProductComparison result = compareSingle(fund, "0", "1000", 2);

        assertThat(result.finalCapital()).isEqualByComparingTo("2010.00");
    }

    // =========================================================================
    // Response shape: sorting, totalPaidIn, legal maximum
    // =========================================================================

    @Test
    void products_are_sorted_by_final_capital_descending_regardless_of_request_order() {
        Pillar3Product low = product(UUID.randomUUID(), "Cash", "0", "0.000", true);
        Pillar3Product high = product(UUID.randomUUID(), "Equity 100", "100", "0.000", true);
        List<UUID> idsLowFirst = List.of(low.getId(), high.getId());
        given(productRepository.findAllById(idsLowFirst)).willReturn(List.of(low, high));

        Pillar3CompareResponse response = compareService.compare(request("1000", "0", 1, idsLowFirst));

        assertThat(response.products())
                .extracting(Pillar3ProductComparison::productId)
                .containsExactly(high.getId(), low.getId());
    }

    @Test
    void total_paid_in_is_the_current_balance_plus_contributions_over_the_horizon() {
        Pillar3Product fund = product(UUID.randomUUID(), "Cash", "0", "0.000", true);
        given(productRepository.findAllById(List.of(fund.getId()))).willReturn(List.of(fund));

        // 1000 + 500 × 3 = 2500
        Pillar3CompareResponse response = compareService.compare(
                request("1000", "500", 3, List.of(fund.getId())));

        assertThat(response.totalPaidIn()).isEqualByComparingTo("2500.00");
    }

    @Test
    void response_exposes_the_legal_maximum_contribution() {
        Pillar3Product fund = product(UUID.randomUUID(), "Cash", "0", "0.000", true);
        given(productRepository.findAllById(List.of(fund.getId()))).willReturn(List.of(fund));

        Pillar3CompareResponse response = compareService.compare(
                request("0", "0", 1, List.of(fund.getId())));

        assertThat(response.maxAnnualContribution()).isEqualByComparingTo("7258");
    }

    // =========================================================================
    // Product resolution
    // =========================================================================

    @Test
    void unknown_product_id_fails_with_a_not_found_error_naming_the_id() {
        Pillar3Product known = product(UUID.randomUUID(), "Known", "45", "0.45", true);
        UUID missingId = UUID.randomUUID();
        List<UUID> ids = List.of(known.getId(), missingId);
        given(productRepository.findAllById(ids)).willReturn(List.of(known));

        assertThatThrownBy(() -> compareService.compare(request("1000", "0", 1, ids)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(missingId.toString());
    }

    @Test
    void inactive_product_remains_comparable() {
        // A stored selection must keep working after an admin deactivates a product.
        Pillar3Product deactivated = product(UUID.randomUUID(), "Retired Fund", "45", "0.45", false);

        Pillar3ProductComparison result = compareSingle(deactivated, "1000", "0", 1);

        assertThat(result.productId()).isEqualTo(deactivated.getId());
    }
}
