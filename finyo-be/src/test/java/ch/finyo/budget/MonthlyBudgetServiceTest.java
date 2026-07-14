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
 * Pure unit tests for MonthlyBudgetService.
 *
 * Focus areas:
 *   1. available = netIncome - sum(positions) - fixedCostsPerMonth
 *      (may be negative, returned as-is).
 *   2. Default view (netIncome 0, no positions, current fixed costs)
 *      when no monthly_budget row exists.
 *   3. Upsert semantics for the net income: first PUT creates the row,
 *      later PUTs reuse its id.
 *   4. Position CRUD: ownership, case-insensitive duplicate names,
 *      sort order assignment, positions without a monthly_budget row.
 */
@ExtendWith(MockitoExtension.class)
class MonthlyBudgetServiceTest {

    private static final String USER_ID = "user-monthly-budget-1";

    @Mock
    private MonthlyBudgetRepository monthlyBudgetRepository;

    @Mock
    private MonthlyBudgetPositionRepository positionRepository;

    @Mock
    private FixedCostService fixedCostService;

    @InjectMocks
    private MonthlyBudgetService monthlyBudgetService;

    private MonthlyBudget budget(UUID id) {
        return MonthlyBudget.builder()
                .id(id)
                .userId(USER_ID)
                .netIncome(new BigDecimal("6000"))
                .build();
    }

    private MonthlyBudgetPosition position(UUID id, String name, String amount, int sortOrder) {
        return MonthlyBudgetPosition.builder()
                .id(id)
                .userId(USER_ID)
                .name(name)
                .amount(new BigDecimal(amount))
                .sortOrder(sortOrder)
                .build();
    }

    // =========================================================================
    // get()
    // =========================================================================

    @Test
    void get_computes_available_from_positions_and_fixed_costs() {
        given(fixedCostService.getTotalPerMonth(USER_ID)).willReturn(new BigDecimal("100.00"));
        given(monthlyBudgetRepository.findByUserId(USER_ID)).willReturn(Optional.of(budget(UUID.randomUUID())));
        given(positionRepository.findByUserIdOrderBySortOrderAscNameAsc(USER_ID)).willReturn(List.of(
                position(UUID.randomUUID(), "Sparen", "500", 0),
                position(UUID.randomUUID(), "Investieren", "400", 1),
                position(UUID.randomUUID(), "Säule 3a", "588", 2)));

        MonthlyBudgetResponse result = monthlyBudgetService.get(USER_ID);

        assertThat(result.positions()).extracting(MonthlyBudgetPositionResponse::name)
                .containsExactly("Sparen", "Investieren", "Säule 3a");
        assertThat(result.fixedCostsPerMonth()).isEqualByComparingTo("100.00");
        // 6000 - 500 - 400 - 588 - 100 = 4412
        assertThat(result.available()).isEqualByComparingTo("4412.00");
    }

    @Test
    void get_returns_zero_net_income_and_no_positions_when_no_data_exists() {
        given(fixedCostService.getTotalPerMonth(USER_ID)).willReturn(new BigDecimal("150.00"));
        given(monthlyBudgetRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(positionRepository.findByUserIdOrderBySortOrderAscNameAsc(USER_ID)).willReturn(List.of());

        MonthlyBudgetResponse result = monthlyBudgetService.get(USER_ID);

        assertThat(result.netIncome()).isEqualByComparingTo("0");
        assertThat(result.positions()).isEmpty();
        assertThat(result.fixedCostsPerMonth()).isEqualByComparingTo("150.00");
        assertThat(result.available()).isEqualByComparingTo("-150.00");
    }

    @Test
    void get_returns_a_negative_available_as_is_when_the_plan_is_over_committed() {
        given(fixedCostService.getTotalPerMonth(USER_ID)).willReturn(new BigDecimal("3000.00"));
        given(monthlyBudgetRepository.findByUserId(USER_ID)).willReturn(Optional.of(MonthlyBudget.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .netIncome(new BigDecimal("4000"))
                .build()));
        given(positionRepository.findByUserIdOrderBySortOrderAscNameAsc(USER_ID)).willReturn(List.of(
                position(UUID.randomUUID(), "Sparen", "1500", 0)));

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
        given(positionRepository.findByUserIdOrderBySortOrderAscNameAsc(USER_ID)).willReturn(List.of());
        given(monthlyBudgetRepository.save(any(MonthlyBudget.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        monthlyBudgetService.upsert(new MonthlyBudgetRequest(new BigDecimal("6000")), USER_ID);

        then(monthlyBudgetRepository).should()
                .save(argThat(b -> b.getId() == null && USER_ID.equals(b.getUserId())
                        && new BigDecimal("6000").compareTo(b.getNetIncome()) == 0));
    }

    @Test
    void upsert_reuses_the_existing_row_id_on_subsequent_puts() {
        UUID existingId = UUID.randomUUID();
        given(fixedCostService.getTotalPerMonth(USER_ID)).willReturn(BigDecimal.ZERO);
        given(monthlyBudgetRepository.findByUserId(USER_ID)).willReturn(Optional.of(budget(existingId)));
        given(positionRepository.findByUserIdOrderBySortOrderAscNameAsc(USER_ID)).willReturn(List.of());
        given(monthlyBudgetRepository.save(any(MonthlyBudget.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        MonthlyBudgetResponse result = monthlyBudgetService.upsert(
                new MonthlyBudgetRequest(new BigDecimal("6500")), USER_ID);

        assertThat(result.netIncome()).isEqualByComparingTo("6000");
        then(monthlyBudgetRepository).should()
                .save(argThat(b -> existingId.equals(b.getId()) && USER_ID.equals(b.getUserId())));
    }

    // =========================================================================
    // createPosition()
    // =========================================================================

    @Test
    void createPosition_assigns_the_next_sort_order_after_the_current_maximum() {
        given(fixedCostService.getTotalPerMonth(USER_ID)).willReturn(BigDecimal.ZERO);
        given(monthlyBudgetRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(positionRepository.existsByUserIdAndNameIgnoreCase(USER_ID, "Ferien")).willReturn(false);
        given(positionRepository.findTopByUserIdOrderBySortOrderDesc(USER_ID))
                .willReturn(Optional.of(position(UUID.randomUUID(), "Sparen", "500", 4)));
        given(positionRepository.saveAndFlush(any(MonthlyBudgetPosition.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(positionRepository.findByUserIdOrderBySortOrderAscNameAsc(USER_ID)).willReturn(List.of());

        monthlyBudgetService.createPosition(
                new MonthlyBudgetPositionRequest("Ferien", new BigDecimal("300")), USER_ID);

        then(positionRepository).should()
                .saveAndFlush(argThat(p -> p.getSortOrder() == 5 && USER_ID.equals(p.getUserId())
                        && "Ferien".equals(p.getName())));
    }

    @Test
    void createPosition_works_without_a_monthly_budget_row_and_starts_at_sort_order_zero() {
        given(fixedCostService.getTotalPerMonth(USER_ID)).willReturn(BigDecimal.ZERO);
        given(monthlyBudgetRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(positionRepository.existsByUserIdAndNameIgnoreCase(USER_ID, "Sparen")).willReturn(false);
        given(positionRepository.findTopByUserIdOrderBySortOrderDesc(USER_ID)).willReturn(Optional.empty());
        given(positionRepository.saveAndFlush(any(MonthlyBudgetPosition.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        var saved = position(UUID.randomUUID(), "Sparen", "500", 0);
        given(positionRepository.findByUserIdOrderBySortOrderAscNameAsc(USER_ID)).willReturn(List.of(saved));

        MonthlyBudgetResponse result = monthlyBudgetService.createPosition(
                new MonthlyBudgetPositionRequest("Sparen", new BigDecimal("500")), USER_ID);

        assertThat(result.netIncome()).isEqualByComparingTo("0");
        assertThat(result.positions()).hasSize(1);
        assertThat(result.available()).isEqualByComparingTo("-500");
        then(positionRepository).should().saveAndFlush(argThat(p -> p.getSortOrder() == 0));
    }

    @Test
    void createPosition_rejects_a_duplicate_name_case_insensitively() {
        given(positionRepository.existsByUserIdAndNameIgnoreCase(USER_ID, "sparen")).willReturn(true);

        assertThatThrownBy(() -> monthlyBudgetService.createPosition(
                new MonthlyBudgetPositionRequest("sparen", new BigDecimal("100")), USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A budget position with this name already exists");
        then(positionRepository).should(never()).saveAndFlush(any());
    }

    // =========================================================================
    // updatePosition()
    // =========================================================================

    @Test
    void updatePosition_keeps_id_owner_and_sort_order_of_the_existing_row() {
        UUID id = UUID.randomUUID();
        given(fixedCostService.getTotalPerMonth(USER_ID)).willReturn(BigDecimal.ZERO);
        given(monthlyBudgetRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(positionRepository.findByIdAndUserId(id, USER_ID))
                .willReturn(Optional.of(position(id, "Sparen", "500", 3)));
        given(positionRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(USER_ID, "Sparen neu", id))
                .willReturn(false);
        given(positionRepository.saveAndFlush(any(MonthlyBudgetPosition.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(positionRepository.findByUserIdOrderBySortOrderAscNameAsc(USER_ID)).willReturn(List.of());

        monthlyBudgetService.updatePosition(id,
                new MonthlyBudgetPositionRequest("Sparen neu", new BigDecimal("600")), USER_ID);

        then(positionRepository).should().saveAndFlush(argThat(p ->
                id.equals(p.getId()) && USER_ID.equals(p.getUserId())
                        && p.getSortOrder() == 3 && "Sparen neu".equals(p.getName())));
    }

    @Test
    void updatePosition_allows_keeping_the_own_name() {
        UUID id = UUID.randomUUID();
        given(fixedCostService.getTotalPerMonth(USER_ID)).willReturn(BigDecimal.ZERO);
        given(monthlyBudgetRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(positionRepository.findByIdAndUserId(id, USER_ID))
                .willReturn(Optional.of(position(id, "Sparen", "500", 0)));
        // the duplicate check excludes the row itself
        given(positionRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(USER_ID, "Sparen", id))
                .willReturn(false);
        given(positionRepository.saveAndFlush(any(MonthlyBudgetPosition.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(positionRepository.findByUserIdOrderBySortOrderAscNameAsc(USER_ID)).willReturn(List.of());

        monthlyBudgetService.updatePosition(id,
                new MonthlyBudgetPositionRequest("Sparen", new BigDecimal("550")), USER_ID);

        then(positionRepository).should().saveAndFlush(any(MonthlyBudgetPosition.class));
    }

    @Test
    void updatePosition_rejects_a_name_already_used_by_another_position() {
        UUID id = UUID.randomUUID();
        given(positionRepository.findByIdAndUserId(id, USER_ID))
                .willReturn(Optional.of(position(id, "Sparen", "500", 0)));
        given(positionRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(USER_ID, "INVESTIEREN", id))
                .willReturn(true);

        assertThatThrownBy(() -> monthlyBudgetService.updatePosition(id,
                new MonthlyBudgetPositionRequest("INVESTIEREN", new BigDecimal("100")), USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A budget position with this name already exists");
    }

    @Test
    void updatePosition_throws_not_found_for_a_foreign_or_unknown_id() {
        UUID id = UUID.randomUUID();
        given(positionRepository.findByIdAndUserId(id, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> monthlyBudgetService.updatePosition(id,
                new MonthlyBudgetPositionRequest("Sparen", BigDecimal.ONE), USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // =========================================================================
    // deletePosition()
    // =========================================================================

    @Test
    void deletePosition_removes_the_row_and_returns_the_rebuilt_aggregate() {
        UUID id = UUID.randomUUID();
        given(fixedCostService.getTotalPerMonth(USER_ID)).willReturn(BigDecimal.ZERO);
        given(monthlyBudgetRepository.findByUserId(USER_ID)).willReturn(Optional.of(budget(UUID.randomUUID())));
        given(positionRepository.findByIdAndUserId(id, USER_ID))
                .willReturn(Optional.of(position(id, "Sparen", "500", 0)));
        given(positionRepository.findByUserIdOrderBySortOrderAscNameAsc(USER_ID)).willReturn(List.of());

        MonthlyBudgetResponse result = monthlyBudgetService.deletePosition(id, USER_ID);

        assertThat(result.positions()).isEmpty();
        assertThat(result.available()).isEqualByComparingTo("6000");
        then(positionRepository).should().deleteById(id);
    }

    @Test
    void deletePosition_throws_not_found_for_a_foreign_or_unknown_id() {
        UUID id = UUID.randomUUID();
        given(positionRepository.findByIdAndUserId(id, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> monthlyBudgetService.deletePosition(id, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        then(positionRepository).should(never()).deleteById(any(UUID.class));
    }
}
