package ch.finyo.analytics;

import ch.finyo.account.Account;
import ch.finyo.account.AccountType;
import ch.finyo.category.Category;
import ch.finyo.category.CategoryRepository;
import ch.finyo.category.CategoryType;
import ch.finyo.transaction.Transaction;
import ch.finyo.transaction.TransactionRepository;
import ch.finyo.transaction.TransactionSource;
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
import static org.assertj.core.api.Assertions.within;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * Pure unit tests for AnalyticsService.
 *
 * Focus areas:
 *   1. getSummary(): income/expense split by amount sign, custom date range
 *      overriding the TimeRange, empty result handling.
 *   2. getCategoryBreakdown(): percentage derivation, deleted-category fallback
 *      to "Uncategorized", descending sort by total, zero-total guard.
 *   3. getMonthlyOverview(): mapping of raw aggregation rows including null
 *      sums (months with only income or only expenses).
 *
 * Custom from/to dates are used wherever possible so the tests do not depend
 * on the actual clock; TimeRange itself is covered in TimeRangeTest.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    private static final String USER_ID = "user-analytics-1";
    private static final LocalDate FROM = LocalDate.of(2025, Month.MARCH, 1);
    private static final LocalDate TO = LocalDate.of(2025, Month.MARCH, 31);

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private Transaction buildTransaction(String amount) {
        Account account = Account.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .name("Checking")
                .type(AccountType.CHECKING)
                .currency("CHF")
                .initialBalance(BigDecimal.ZERO)
                .build();
        return Transaction.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .amount(new BigDecimal(amount))
                .currency("CHF")
                .date(LocalDate.of(2025, Month.MARCH, 15))
                .account(account)
                .source(TransactionSource.MANUAL)
                .build();
    }

    private Category buildCategory(UUID id, String name, String color) {
        return Category.builder()
                .id(id)
                .userId(USER_ID)
                .name(name)
                .color(color)
                .type(CategoryType.EXPENSE)
                .build();
    }

    // =========================================================================
    // getSummary()
    // =========================================================================

    @Test
    void getSummary_splits_income_and_expenses_by_amount_sign() {
        given(transactionRepository.findByUserIdAndDateBetweenOrderByDateDesc(USER_ID, FROM, TO))
                .willReturn(List.of(
                        buildTransaction("5000.00"),
                        buildTransaction("-1200.00"),
                        buildTransaction("-800.00")));

        SpendingSummaryResponse result = analyticsService.getSummary(USER_ID, null, FROM, TO);

        assertThat(result.totalIncome()).isEqualByComparingTo("5000.00");
        assertThat(result.totalExpenses()).isEqualByComparingTo("2000.00");
        assertThat(result.netAmount()).isEqualByComparingTo("3000.00");
        assertThat(result.transactionCount()).isEqualTo(3);
        assertThat(result.rangeStart()).isEqualTo(FROM);
        assertThat(result.rangeEnd()).isEqualTo(TO);
    }

    @Test
    void getSummary_returns_zero_totals_when_the_period_has_no_transactions() {
        given(transactionRepository.findByUserIdAndDateBetweenOrderByDateDesc(USER_ID, FROM, TO))
                .willReturn(List.of());

        SpendingSummaryResponse result = analyticsService.getSummary(USER_ID, null, FROM, TO);

        assertThat(result.totalIncome()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.totalExpenses()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.netAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.transactionCount()).isZero();
    }

    @Test
    void getSummary_prefers_custom_dates_over_the_time_range() {
        given(transactionRepository.findByUserIdAndDateBetweenOrderByDateDesc(USER_ID, FROM, TO))
                .willReturn(List.of());

        analyticsService.getSummary(USER_ID, TimeRange.LAST_12_MONTHS, FROM, TO);

        then(transactionRepository).should()
                .findByUserIdAndDateBetweenOrderByDateDesc(USER_ID, FROM, TO);
    }

    @Test
    void getSummary_falls_back_to_the_time_range_when_no_custom_dates_are_given() {
        LocalDate expectedFrom = TimeRange.THIS_MONTH.startDate();
        LocalDate expectedTo = TimeRange.THIS_MONTH.endDate();
        given(transactionRepository.findByUserIdAndDateBetweenOrderByDateDesc(USER_ID, expectedFrom, expectedTo))
                .willReturn(List.of());

        SpendingSummaryResponse result = analyticsService.getSummary(USER_ID, TimeRange.THIS_MONTH, null, null);

        assertThat(result.rangeStart()).isEqualTo(expectedFrom);
        assertThat(result.rangeEnd()).isEqualTo(expectedTo);
    }

    // =========================================================================
    // getCategoryBreakdown()
    // =========================================================================

    @Test
    void getCategoryBreakdown_computes_percentages_and_sorts_by_total_descending() {
        UUID groceriesId = UUID.randomUUID();
        UUID diningId = UUID.randomUUID();
        given(transactionRepository.sumExpensesByCategoryForPeriod(USER_ID, FROM, TO))
                .willReturn(List.<Object[]>of(
                        new Object[]{groceriesId, new BigDecimal("-250.00")},
                        new Object[]{diningId, new BigDecimal("-750.00")}));
        given(categoryRepository.findById(groceriesId))
                .willReturn(Optional.of(buildCategory(groceriesId, "Groceries", "#8b5cf6")));
        given(categoryRepository.findById(diningId))
                .willReturn(Optional.of(buildCategory(diningId, "Dining", "#f97316")));

        List<CategoryBreakdownItem> result = analyticsService.getCategoryBreakdown(USER_ID, null, FROM, TO);

        assertThat(result).hasSize(2);
        CategoryBreakdownItem top = result.get(0);
        assertThat(top.categoryName()).isEqualTo("Dining");
        assertThat(top.categoryColor()).isEqualTo("#f97316");
        assertThat(top.total()).isEqualByComparingTo("750.00");
        assertThat(top.percentage()).isCloseTo(75.0, within(0.001));
        assertThat(result.get(1).percentage()).isCloseTo(25.0, within(0.001));
    }

    @Test
    void getCategoryBreakdown_labels_a_deleted_category_as_uncategorized() {
        UUID vanishedCategoryId = UUID.randomUUID();
        given(transactionRepository.sumExpensesByCategoryForPeriod(USER_ID, FROM, TO))
                .willReturn(List.<Object[]>of(new Object[]{vanishedCategoryId, new BigDecimal("-100.00")}));
        given(categoryRepository.findById(vanishedCategoryId)).willReturn(Optional.empty());

        List<CategoryBreakdownItem> result = analyticsService.getCategoryBreakdown(USER_ID, null, FROM, TO);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).categoryName()).isEqualTo("Uncategorized");
        assertThat(result.get(0).categoryColor()).isNull();
    }

    @Test
    void getCategoryBreakdown_groups_uncategorized_expenses_under_a_null_category_id() {
        // the aggregation now includes transactions without a category as a
        // NULL group — no repository lookup must happen for it
        UUID groceriesId = UUID.randomUUID();
        given(transactionRepository.sumExpensesByCategoryForPeriod(USER_ID, FROM, TO))
                .willReturn(List.<Object[]>of(
                        new Object[]{null, new BigDecimal("-300.00")},
                        new Object[]{groceriesId, new BigDecimal("-100.00")}));
        given(categoryRepository.findById(groceriesId))
                .willReturn(Optional.of(buildCategory(groceriesId, "Groceries", "#8b5cf6")));

        List<CategoryBreakdownItem> result = analyticsService.getCategoryBreakdown(USER_ID, null, FROM, TO);

        assertThat(result).hasSize(2);
        CategoryBreakdownItem uncategorized = result.get(0);
        assertThat(uncategorized.categoryId()).isNull();
        assertThat(uncategorized.categoryName()).isEqualTo("Uncategorized");
        assertThat(uncategorized.categoryColor()).isNull();
        assertThat(uncategorized.total()).isEqualByComparingTo("300.00");
        assertThat(uncategorized.percentage()).isCloseTo(75.0, within(0.001));
        then(categoryRepository).should().findById(groceriesId);
        then(categoryRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    void getCategoryBreakdown_returns_empty_list_when_there_are_no_expenses() {
        given(transactionRepository.sumExpensesByCategoryForPeriod(USER_ID, FROM, TO))
                .willReturn(List.of());

        assertThat(analyticsService.getCategoryBreakdown(USER_ID, null, FROM, TO)).isEmpty();
    }

    // =========================================================================
    // getMonthlyOverview()
    // =========================================================================

    @Test
    void getMonthlyOverview_maps_aggregation_rows_to_monthly_data_points() {
        TimeRange range = TimeRange.LAST_3_MONTHS;
        given(transactionRepository.monthlyTotalsForPeriod(USER_ID, range.startDate(), range.endDate()))
                .willReturn(List.<Object[]>of(
                        new Object[]{2025, 2, new BigDecimal("-1500.00"), new BigDecimal("6000.00")},
                        new Object[]{2025, 3, new BigDecimal("-1800.00"), new BigDecimal("6000.00")}));

        List<MonthlyDataPoint> result = analyticsService.getMonthlyOverview(USER_ID, range);

        assertThat(result).hasSize(2);
        MonthlyDataPoint february = result.get(0);
        assertThat(february.year()).isEqualTo(2025);
        assertThat(february.month()).isEqualTo(Month.FEBRUARY.getValue());
        assertThat(february.expenses()).isEqualByComparingTo("1500.00");
        assertThat(february.income()).isEqualByComparingTo("6000.00");
        assertThat(february.net()).isEqualByComparingTo("4500.00");
    }

    @Test
    void getMonthlyOverview_treats_null_sums_as_zero() {
        // A month can have only income (expenses NULL) or only expenses (income NULL)
        TimeRange range = TimeRange.LAST_3_MONTHS;
        given(transactionRepository.monthlyTotalsForPeriod(USER_ID, range.startDate(), range.endDate()))
                .willReturn(List.<Object[]>of(new Object[]{2025, 4, null, null}));

        List<MonthlyDataPoint> result = analyticsService.getMonthlyOverview(USER_ID, range);

        MonthlyDataPoint april = result.get(0);
        assertThat(april.expenses()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(april.income()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(april.net()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
