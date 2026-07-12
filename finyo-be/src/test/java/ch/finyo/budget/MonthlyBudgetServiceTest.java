package ch.finyo.budget;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * Pure unit tests for MonthlyBudgetService.
 *
 * Focus areas:
 *   1. available = netIncome - savings - investing - pillar3a - taxReserve
 *      - workCosts - fixedCostsPerMonth (may be negative, returned as-is).
 *   2. Default all-zero view (plus current fixed costs) when no row exists.
 *   3. Upsert semantics: first PUT creates the row, later PUTs reuse its id.
 */
@ExtendWith(MockitoExtension.class)
class MonthlyBudgetServiceTest {

    private static final String USER_ID = "user-monthly-budget-1";

    @Mock
    private MonthlyBudgetRepository monthlyBudgetRepository;

    @Mock
    private FixedCostService fixedCostService;

    @InjectMocks
    private MonthlyBudgetService monthlyBudgetService;

    private MonthlyBudget budget(UUID id) {
        return MonthlyBudget.builder()
                .id(id)
                .userId(USER_ID)
                .netIncome(new BigDecimal("6000"))
                .savings(new BigDecimal("500"))
                .investing(new BigDecimal("400"))
                .pillar3a(new BigDecimal("588"))
                .taxReserve(new BigDecimal("800"))
                .workCosts(new BigDecimal("200"))
                .build();
    }

    // =========================================================================
    // get()
    // =========================================================================

    @Test
    void get_computes_available_from_all_deductions_and_fixed_costs() {
        given(fixedCostService.getTotalPerMonth(USER_ID)).willReturn(new BigDecimal("100.00"));
        given(monthlyBudgetRepository.findByUserId(USER_ID)).willReturn(Optional.of(budget(UUID.randomUUID())));

        MonthlyBudgetResponse result = monthlyBudgetService.get(USER_ID);

        assertThat(result.fixedCostsPerMonth()).isEqualByComparingTo("100.00");
        // 6000 - 500 - 400 - 588 - 800 - 200 - 100 = 3412
        assertThat(result.available()).isEqualByComparingTo("3412.00");
    }

    @Test
    void get_returns_zeros_plus_current_fixed_costs_when_no_budget_row_exists() {
        given(fixedCostService.getTotalPerMonth(USER_ID)).willReturn(new BigDecimal("150.00"));
        given(monthlyBudgetRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        MonthlyBudgetResponse result = monthlyBudgetService.get(USER_ID);

        assertThat(result.netIncome()).isEqualByComparingTo("0");
        assertThat(result.savings()).isEqualByComparingTo("0");
        assertThat(result.investing()).isEqualByComparingTo("0");
        assertThat(result.pillar3a()).isEqualByComparingTo("0");
        assertThat(result.taxReserve()).isEqualByComparingTo("0");
        assertThat(result.workCosts()).isEqualByComparingTo("0");
        assertThat(result.fixedCostsPerMonth()).isEqualByComparingTo("150.00");
        assertThat(result.available()).isEqualByComparingTo("-150.00");
    }

    @Test
    void get_returns_a_negative_available_as_is_when_the_plan_is_over_committed() {
        given(fixedCostService.getTotalPerMonth(USER_ID)).willReturn(new BigDecimal("3000.00"));
        MonthlyBudget overCommitted = MonthlyBudget.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .netIncome(new BigDecimal("4000"))
                .savings(new BigDecimal("1000"))
                .investing(new BigDecimal("500"))
                .pillar3a(BigDecimal.ZERO)
                .taxReserve(BigDecimal.ZERO)
                .workCosts(BigDecimal.ZERO)
                .build();
        given(monthlyBudgetRepository.findByUserId(USER_ID)).willReturn(Optional.of(overCommitted));

        MonthlyBudgetResponse result = monthlyBudgetService.get(USER_ID);

        assertThat(result.available()).isEqualByComparingTo("-500.00");
    }

    // =========================================================================
    // upsert()
    // =========================================================================

    @Test
    void upsert_creates_a_new_row_on_first_put() {
        given(fixedCostService.getTotalPerMonth(USER_ID)).willReturn(BigDecimal.ZERO);
        given(monthlyBudgetRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(monthlyBudgetRepository.save(any(MonthlyBudget.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        MonthlyBudgetRequest request = new MonthlyBudgetRequest(
                new BigDecimal("6000"), new BigDecimal("500"), new BigDecimal("400"),
                new BigDecimal("588"), new BigDecimal("800"), new BigDecimal("200"));
        MonthlyBudgetResponse result = monthlyBudgetService.upsert(request, USER_ID);

        assertThat(result.available()).isEqualByComparingTo("3512.00");
        then(monthlyBudgetRepository).should()
                .save(argThat(b -> b.getId() == null && USER_ID.equals(b.getUserId())));
    }

    @Test
    void upsert_reuses_the_existing_row_id_on_subsequent_puts() {
        UUID existingId = UUID.randomUUID();
        given(fixedCostService.getTotalPerMonth(USER_ID)).willReturn(BigDecimal.ZERO);
        given(monthlyBudgetRepository.findByUserId(USER_ID)).willReturn(Optional.of(budget(existingId)));
        given(monthlyBudgetRepository.save(any(MonthlyBudget.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        MonthlyBudgetRequest request = new MonthlyBudgetRequest(
                new BigDecimal("6500"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        MonthlyBudgetResponse result = monthlyBudgetService.upsert(request, USER_ID);

        assertThat(result.netIncome()).isEqualByComparingTo("6500");
        then(monthlyBudgetRepository).should()
                .save(argThat(b -> existingId.equals(b.getId()) && USER_ID.equals(b.getUserId())));
    }
}
