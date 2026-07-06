package ch.finyo.budget;

import ch.finyo.category.Category;
import ch.finyo.category.CategoryRepository;
import ch.finyo.category.CategoryType;
import ch.finyo.common.ResourceNotFoundException;
import ch.finyo.transaction.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.anyString;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

/**
 * Pure unit tests for BudgetService.
 *
 * Focus areas:
 *   1. getCurrentStatus() joins active budgets with the month-to-date expense
 *      sums per category: spent/remaining/percentage/overBudget derivation,
 *      zero-amount guard, missing-spending default, 999.99 percentage cap.
 *   2. Category resolution on create/update is scoped to the user.
 *   3. Standard CRUD multi-tenancy and not-found semantics.
 */
@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    private static final String USER_ID = "user-budget-1";

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private BudgetService budgetService;

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private Category buildCategory(UUID id, String name) {
        return Category.builder()
                .id(id)
                .userId(USER_ID)
                .name(name)
                .color("#3b82f6")
                .type(CategoryType.EXPENSE)
                .build();
    }

    private Budget buildBudget(UUID id, Category category, String amount) {
        return Budget.builder()
                .id(id)
                .userId(USER_ID)
                .category(category)
                .amount(new BigDecimal(amount))
                .period(BudgetPeriod.MONTHLY)
                .validFrom(LocalDate.of(2025, Month.JANUARY, 1))
                .build();
    }

    private BudgetRequest requestFor(UUID categoryId) {
        return new BudgetRequest(
                categoryId, new BigDecimal("500"), BudgetPeriod.MONTHLY,
                LocalDate.of(2025, Month.JANUARY, 1), null);
    }

    // =========================================================================
    // getAll() / getById()
    // =========================================================================

    @Test
    void getAll_maps_budgets_with_their_category_details() {
        UUID categoryId = UUID.randomUUID();
        Budget budget = buildBudget(UUID.randomUUID(), buildCategory(categoryId, "Groceries"), "600");
        given(budgetRepository.findByUserIdOrderByCategoryNameAsc(USER_ID)).willReturn(List.of(budget));

        List<BudgetResponse> result = budgetService.getAll(USER_ID);

        assertThat(result).hasSize(1);
        BudgetResponse response = result.get(0);
        assertThat(response.categoryId()).isEqualTo(categoryId);
        assertThat(response.categoryName()).isEqualTo("Groceries");
        assertThat(response.amount()).isEqualByComparingTo("600");
    }

    @Test
    void getById_throws_ResourceNotFoundException_when_budget_belongs_to_another_user() {
        UUID id = UUID.randomUUID();
        given(budgetRepository.findByIdAndUserId(id, "attacker")).willReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.getById(id, "attacker"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Budget");
    }

    // =========================================================================
    // getCurrentStatus()
    // =========================================================================

    @Test
    void getCurrentStatus_computes_spent_remaining_and_percentage_from_category_spending() {
        UUID categoryId = UUID.randomUUID();
        Budget budget = buildBudget(UUID.randomUUID(), buildCategory(categoryId, "Groceries"), "600");
        given(budgetRepository.findActiveBudgetsForUserAndDate(anyString(), any(LocalDate.class)))
                .willReturn(List.of(budget));
        // Expense sums are negative in the DB; the service must take the absolute value
        given(transactionRepository.sumExpensesByCategoryForPeriod(anyString(), any(), any()))
                .willReturn(List.<Object[]>of(new Object[]{categoryId, new BigDecimal("-150.00")}));

        List<BudgetStatusResponse> result = budgetService.getCurrentStatus(USER_ID);

        assertThat(result).hasSize(1);
        BudgetStatusResponse status = result.get(0);
        assertThat(status.spent()).isEqualByComparingTo("150.00");
        assertThat(status.remaining()).isEqualByComparingTo("450.00");
        assertThat(status.percentageUsed()).isCloseTo(25.0, within(0.001));
        assertThat(status.overBudget()).isFalse();
        assertThat(status.categoryName()).isEqualTo("Groceries");
    }

    @Test
    void getCurrentStatus_defaults_spent_to_zero_when_category_has_no_expenses_this_month() {
        UUID categoryId = UUID.randomUUID();
        Budget budget = buildBudget(UUID.randomUUID(), buildCategory(categoryId, "Travel"), "300");
        given(budgetRepository.findActiveBudgetsForUserAndDate(anyString(), any(LocalDate.class)))
                .willReturn(List.of(budget));
        given(transactionRepository.sumExpensesByCategoryForPeriod(anyString(), any(), any()))
                .willReturn(List.of());

        List<BudgetStatusResponse> result = budgetService.getCurrentStatus(USER_ID);

        BudgetStatusResponse status = result.get(0);
        assertThat(status.spent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(status.remaining()).isEqualByComparingTo("300");
        assertThat(status.percentageUsed()).isZero();
    }

    @Test
    void getCurrentStatus_flags_over_budget_and_caps_percentage_at_999_99() {
        UUID categoryId = UUID.randomUUID();
        Budget budget = buildBudget(UUID.randomUUID(), buildCategory(categoryId, "Dining"), "10");
        given(budgetRepository.findActiveBudgetsForUserAndDate(anyString(), any(LocalDate.class)))
                .willReturn(List.of(budget));
        given(transactionRepository.sumExpensesByCategoryForPeriod(anyString(), any(), any()))
                .willReturn(List.<Object[]>of(new Object[]{categoryId, new BigDecimal("-5000.00")}));

        List<BudgetStatusResponse> result = budgetService.getCurrentStatus(USER_ID);

        BudgetStatusResponse status = result.get(0);
        assertThat(status.overBudget()).isTrue();
        assertThat(status.remaining()).isEqualByComparingTo("-4990.00");
        assertThat(status.percentageUsed()).isEqualTo(999.99);
    }

    @Test
    void getCurrentStatus_reports_zero_percentage_for_a_zero_amount_budget() {
        UUID categoryId = UUID.randomUUID();
        Budget budget = buildBudget(UUID.randomUUID(), buildCategory(categoryId, "Misc"), "0");
        given(budgetRepository.findActiveBudgetsForUserAndDate(anyString(), any(LocalDate.class)))
                .willReturn(List.of(budget));
        given(transactionRepository.sumExpensesByCategoryForPeriod(anyString(), any(), any()))
                .willReturn(List.<Object[]>of(new Object[]{categoryId, new BigDecimal("-20.00")}));

        List<BudgetStatusResponse> result = budgetService.getCurrentStatus(USER_ID);

        assertThat(result.get(0).percentageUsed())
                .as("division-by-zero guard: zero budget must yield 0%, not an exception")
                .isZero();
    }

    @Test
    void getCurrentStatus_ignores_spending_of_categories_without_a_budget() {
        UUID budgetedCategory = UUID.randomUUID();
        UUID unbudgetedCategory = UUID.randomUUID();
        Budget budget = buildBudget(UUID.randomUUID(), buildCategory(budgetedCategory, "Groceries"), "600");
        given(budgetRepository.findActiveBudgetsForUserAndDate(anyString(), any(LocalDate.class)))
                .willReturn(List.of(budget));
        given(transactionRepository.sumExpensesByCategoryForPeriod(anyString(), any(), any()))
                .willReturn(List.<Object[]>of(
                        new Object[]{unbudgetedCategory, new BigDecimal("-999.00")},
                        new Object[]{budgetedCategory, new BigDecimal("-100.00")}));

        List<BudgetStatusResponse> result = budgetService.getCurrentStatus(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).spent()).isEqualByComparingTo("100.00");
    }

    @Test
    void getCurrentStatus_returns_empty_list_when_no_budget_is_active() {
        given(budgetRepository.findActiveBudgetsForUserAndDate(anyString(), any(LocalDate.class)))
                .willReturn(List.of());
        given(transactionRepository.sumExpensesByCategoryForPeriod(anyString(), any(), any()))
                .willReturn(List.of());

        assertThat(budgetService.getCurrentStatus(USER_ID)).isEmpty();
    }

    // =========================================================================
    // create()
    // =========================================================================

    @Test
    void create_sets_userId_and_links_the_resolved_category() {
        UUID categoryId = UUID.randomUUID();
        given(categoryRepository.findByIdAndUserId(categoryId, USER_ID))
                .willReturn(Optional.of(buildCategory(categoryId, "Groceries")));
        given(budgetRepository.save(any(Budget.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        BudgetResponse result = budgetService.create(requestFor(categoryId), USER_ID);

        assertThat(result.categoryId()).isEqualTo(categoryId);
        then(budgetRepository).should().save(argThat(b -> USER_ID.equals(b.getUserId())));
    }

    @Test
    void create_defaults_period_to_monthly_when_not_provided() {
        UUID categoryId = UUID.randomUUID();
        BudgetRequest request = new BudgetRequest(
                categoryId, new BigDecimal("500"), null, LocalDate.of(2025, Month.JANUARY, 1), null);
        given(categoryRepository.findByIdAndUserId(categoryId, USER_ID))
                .willReturn(Optional.of(buildCategory(categoryId, "Groceries")));
        given(budgetRepository.save(any(Budget.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        BudgetResponse result = budgetService.create(request, USER_ID);

        assertThat(result.period()).isEqualTo(BudgetPeriod.MONTHLY);
    }

    @Test
    void create_throws_ResourceNotFoundException_when_category_belongs_to_another_user() {
        UUID categoryId = UUID.randomUUID();
        given(categoryRepository.findByIdAndUserId(categoryId, USER_ID)).willReturn(Optional.empty());
        BudgetRequest request = requestFor(categoryId);

        assertThatThrownBy(() -> budgetService.create(request, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category");
        then(budgetRepository).should(never()).save(any());
    }

    // =========================================================================
    // update()
    // =========================================================================

    @Test
    void update_keeps_existing_period_when_request_omits_it() {
        UUID id = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Category category = buildCategory(categoryId, "Groceries");
        Budget existing = Budget.builder()
                .id(id).userId(USER_ID).category(category)
                .amount(new BigDecimal("400")).period(BudgetPeriod.WEEKLY)
                .validFrom(LocalDate.of(2025, Month.JANUARY, 1))
                .build();
        given(budgetRepository.findByIdAndUserId(id, USER_ID)).willReturn(Optional.of(existing));
        given(categoryRepository.findByIdAndUserId(categoryId, USER_ID)).willReturn(Optional.of(category));
        given(budgetRepository.save(any(Budget.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        BudgetRequest request = new BudgetRequest(
                categoryId, new BigDecimal("450"), null, LocalDate.of(2025, Month.FEBRUARY, 1), null);
        BudgetResponse result = budgetService.update(id, request, USER_ID);

        assertThat(result.period()).isEqualTo(BudgetPeriod.WEEKLY);
        assertThat(result.amount()).isEqualByComparingTo("450");
    }

    @Test
    void update_throws_ResourceNotFoundException_when_budget_is_not_found() {
        UUID id = UUID.randomUUID();
        given(budgetRepository.findByIdAndUserId(id, USER_ID)).willReturn(Optional.empty());
        BudgetRequest request = requestFor(UUID.randomUUID());

        assertThatThrownBy(() -> budgetService.update(id, request, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Budget");
    }

    @Test
    void update_throws_ResourceNotFoundException_when_new_category_belongs_to_another_user() {
        UUID id = UUID.randomUUID();
        UUID foreignCategoryId = UUID.randomUUID();
        Budget existing = buildBudget(id, buildCategory(UUID.randomUUID(), "Own"), "100");
        given(budgetRepository.findByIdAndUserId(id, USER_ID)).willReturn(Optional.of(existing));
        given(categoryRepository.findByIdAndUserId(foreignCategoryId, USER_ID)).willReturn(Optional.empty());
        BudgetRequest request = requestFor(foreignCategoryId);

        assertThatThrownBy(() -> budgetService.update(id, request, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category");
        then(budgetRepository).should(never()).save(any());
    }

    // =========================================================================
    // delete()
    // =========================================================================

    @Test
    void delete_removes_the_budget_when_it_belongs_to_the_user() {
        UUID id = UUID.randomUUID();
        given(budgetRepository.findByIdAndUserId(id, USER_ID))
                .willReturn(Optional.of(buildBudget(id, buildCategory(UUID.randomUUID(), "x"), "100")));

        budgetService.delete(id, USER_ID);

        then(budgetRepository).should().deleteById(id);
    }

    @Test
    void delete_never_calls_deleteById_when_budget_belongs_to_another_user() {
        UUID id = UUID.randomUUID();
        given(budgetRepository.findByIdAndUserId(id, "attacker")).willReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.delete(id, "attacker"))
                .isInstanceOf(ResourceNotFoundException.class);
        then(budgetRepository).should(never()).deleteById(any());
    }
}
