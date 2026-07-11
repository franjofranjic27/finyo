package ch.finyo.budget;

import ch.finyo.common.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

/**
 * Pure unit tests for FixedCostService.
 *
 * Focus areas:
 *   1. Per-year/per-month derivation: MONTHLY costs are annualised (x12),
 *      YEARLY costs are broken down (/12), both rounded to 2 dp HALF_UP.
 *   2. List ordering (amountPerYear DESC, then name) and totals.
 *   3. Standard CRUD multi-tenancy and not-found semantics.
 */
@ExtendWith(MockitoExtension.class)
class FixedCostServiceTest {

    private static final String USER_ID = "user-fixed-cost-1";

    @Mock
    private FixedCostRepository fixedCostRepository;

    @InjectMocks
    private FixedCostService fixedCostService;

    private FixedCost cost(String name, PaymentInterval interval, String amount) {
        return FixedCost.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .name(name)
                .paymentInterval(interval)
                .amount(new BigDecimal(amount))
                .build();
    }

    // =========================================================================
    // per-year / per-month derivation
    // =========================================================================

    @Test
    void getAll_annualises_a_monthly_cost_and_keeps_the_monthly_amount() {
        given(fixedCostRepository.findByUserId(USER_ID))
                .willReturn(List.of(cost("Netflix", PaymentInterval.MONTHLY, "20")));

        FixedCostListResponse result = fixedCostService.getAll(USER_ID);

        FixedCostResponse item = result.items().get(0);
        assertThat(item.amountPerYear()).isEqualByComparingTo("240.00");
        assertThat(item.amountPerMonth()).isEqualByComparingTo("20.00");
        assertThat(item.amount()).isEqualByComparingTo("20");
    }

    @Test
    void getAll_breaks_a_yearly_cost_down_to_a_monthly_amount_with_half_up_rounding() {
        given(fixedCostRepository.findByUserId(USER_ID))
                .willReturn(List.of(cost("Insurance", PaymentInterval.YEARLY, "100")));

        FixedCostListResponse result = fixedCostService.getAll(USER_ID);

        FixedCostResponse item = result.items().get(0);
        assertThat(item.amountPerYear()).isEqualByComparingTo("100.00");
        // 100 / 12 = 8.3333... -> 8.33 HALF_UP
        assertThat(item.amountPerMonth()).isEqualByComparingTo("8.33");
    }

    @Test
    void getAll_rounds_derived_values_to_two_decimals_half_up() {
        given(fixedCostRepository.findByUserId(USER_ID))
                .willReturn(List.of(cost("Cloud", PaymentInterval.MONTHLY, "12.3456")));

        FixedCostListResponse result = fixedCostService.getAll(USER_ID);

        FixedCostResponse item = result.items().get(0);
        // 12.3456 * 12 = 148.1472 -> 148.15
        assertThat(item.amountPerYear()).isEqualByComparingTo("148.15");
        // 148.15 / 12 = 12.3458... -> 12.35
        assertThat(item.amountPerMonth()).isEqualByComparingTo("12.35");
    }

    // =========================================================================
    // ordering and totals
    // =========================================================================

    @Test
    void getAll_sorts_by_amountPerYear_desc_then_name_and_sums_totals() {
        given(fixedCostRepository.findByUserId(USER_ID)).willReturn(List.of(
                cost("B-Subscription", PaymentInterval.MONTHLY, "10"),   // 120 / year
                cost("A-Insurance", PaymentInterval.YEARLY, "120"),      // 120 / year
                cost("C-Rent", PaymentInterval.MONTHLY, "50")));         // 600 / year

        FixedCostListResponse result = fixedCostService.getAll(USER_ID);

        assertThat(result.items())
                .extracting(FixedCostResponse::name)
                .containsExactly("C-Rent", "A-Insurance", "B-Subscription");
        assertThat(result.totalPerYear()).isEqualByComparingTo("840.00");
        assertThat(result.totalPerMonth()).isEqualByComparingTo("70.00");
    }

    @Test
    void getAll_returns_zero_totals_for_a_user_without_fixed_costs() {
        given(fixedCostRepository.findByUserId(USER_ID)).willReturn(List.of());

        FixedCostListResponse result = fixedCostService.getAll(USER_ID);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalPerYear()).isEqualByComparingTo("0");
        assertThat(result.totalPerMonth()).isEqualByComparingTo("0");
    }

    @Test
    void getTotalPerMonth_sums_the_rounded_monthly_amounts_of_all_costs() {
        given(fixedCostRepository.findByUserId(USER_ID)).willReturn(List.of(
                cost("Rent", PaymentInterval.MONTHLY, "1500"),
                cost("Insurance", PaymentInterval.YEARLY, "100")));  // 8.33 / month

        assertThat(fixedCostService.getTotalPerMonth(USER_ID)).isEqualByComparingTo("1508.33");
    }

    // =========================================================================
    // create() / update() / delete()
    // =========================================================================

    @Test
    void create_sets_userId_on_the_persisted_entity() {
        FixedCostRequest request = new FixedCostRequest(
                "Netflix", "Streaming", PaymentInterval.MONTHLY, new BigDecimal("20"));
        given(fixedCostRepository.save(any(FixedCost.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        FixedCostResponse result = fixedCostService.create(request, USER_ID);

        assertThat(result.name()).isEqualTo("Netflix");
        assertThat(result.amountPerYear()).isEqualByComparingTo("240.00");
        then(fixedCostRepository).should().save(argThat(c -> USER_ID.equals(c.getUserId())));
    }

    @Test
    void update_throws_ResourceNotFoundException_when_cost_belongs_to_another_user() {
        UUID id = UUID.randomUUID();
        given(fixedCostRepository.findByIdAndUserId(id, "attacker")).willReturn(Optional.empty());
        FixedCostRequest request = new FixedCostRequest(
                "Netflix", null, PaymentInterval.MONTHLY, new BigDecimal("20"));

        assertThatThrownBy(() -> fixedCostService.update(id, request, "attacker"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("FixedCost");
        then(fixedCostRepository).should(never()).save(any());
    }

    @Test
    void delete_never_calls_deleteById_when_cost_belongs_to_another_user() {
        UUID id = UUID.randomUUID();
        given(fixedCostRepository.findByIdAndUserId(id, "attacker")).willReturn(Optional.empty());

        assertThatThrownBy(() -> fixedCostService.delete(id, "attacker"))
                .isInstanceOf(ResourceNotFoundException.class);
        then(fixedCostRepository).should(never()).deleteById(any());
    }
}
