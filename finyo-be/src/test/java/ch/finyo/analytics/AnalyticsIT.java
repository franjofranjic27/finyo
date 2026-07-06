package ch.finyo.analytics;

import ch.finyo.BaseIntegrationTest;
import ch.finyo.account.Account;
import ch.finyo.account.AccountRepository;
import ch.finyo.account.AccountType;
import ch.finyo.category.Category;
import ch.finyo.category.CategoryRepository;
import ch.finyo.category.CategoryType;
import ch.finyo.common.SwissTime;
import ch.finyo.transaction.Transaction;
import ch.finyo.transaction.TransactionRepository;
import ch.finyo.transaction.TransactionSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for /api/v1/analytics (summary, by-category, monthly).
 *
 * The summary and by-category endpoints are queried with explicit from/to
 * dates over fixed test data, so assertions are exact. Only the monthly
 * endpoint uses a TimeRange and therefore data anchored to "today" in the
 * Swiss time zone.
 */
class AnalyticsIT extends BaseIntegrationTest {

    private static final String FROM = "2025-03-01";
    private static final String TO = "2025-03-31";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private AccountRepository accountRepository;

    private Account account;
    private Category groceries;

    @BeforeEach
    void seedData() {
        transactionRepository.deleteAll();
        categoryRepository.deleteAll();
        accountRepository.deleteAll();

        account = accountRepository.save(Account.builder()
                .userId(TEST_USER_ID)
                .name("Checking")
                .type(AccountType.CHECKING)
                .currency("CHF")
                .initialBalance(BigDecimal.ZERO)
                .build());
        groceries = categoryRepository.save(Category.builder()
                .userId(TEST_USER_ID)
                .name("Groceries")
                .color("#8b5cf6")
                .type(CategoryType.EXPENSE)
                .build());

        saveTransaction("5000.00", LocalDate.of(2025, Month.MARCH, 25), null, "Salary");
        saveTransaction("-1200.00", LocalDate.of(2025, Month.MARCH, 5), groceries, "Groceries A");
        saveTransaction("-800.00", LocalDate.of(2025, Month.MARCH, 12), groceries, "Groceries B");
        // Outside the queried range — must never appear in the results
        saveTransaction("-999.00", LocalDate.of(2025, Month.FEBRUARY, 28), groceries, "Old expense");
    }

    private void saveTransaction(String amount, LocalDate date, Category category, String description) {
        transactionRepository.save(Transaction.builder()
                .userId(TEST_USER_ID)
                .amount(new BigDecimal(amount))
                .currency("CHF")
                .date(date)
                .description(description)
                .category(category)
                .account(account)
                .source(TransactionSource.MANUAL)
                .build());
    }

    @Test
    void summary_reports_income_expenses_and_net_for_the_custom_range() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/summary")
                        .param("from", FROM).param("to", TO)
                        .with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome", is(5000.00)))
                .andExpect(jsonPath("$.totalExpenses", is(2000.00)))
                .andExpect(jsonPath("$.netAmount", is(3000.00)))
                .andExpect(jsonPath("$.transactionCount", is(3)));
    }

    @Test
    void by_category_aggregates_expenses_per_category_with_percentages() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/by-category")
                        .param("from", FROM).param("to", TO)
                        .with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].categoryName", is("Groceries")))
                .andExpect(jsonPath("$[0].total", is(2000.00)))
                .andExpect(jsonPath("$[0].percentage", is(100.0)));
    }

    @Test
    void monthly_returns_a_data_point_for_the_current_month() throws Exception {
        LocalDate today = LocalDate.now(SwissTime.ZONE);
        saveTransaction("-300.00", today, groceries, "Current month expense");
        saveTransaction("4000.00", today, null, "Current month income");

        mockMvc.perform(get("/api/v1/analytics/monthly")
                        .param("range", "LAST_3_MONTHS")
                        .with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].year", is(today.getYear())))
                .andExpect(jsonPath("$[0].month", is(today.getMonthValue())))
                .andExpect(jsonPath("$[0].expenses", is(300.00)))
                .andExpect(jsonPath("$[0].income", is(4000.00)));
    }

    @Test
    void analytics_never_include_another_users_transactions() throws Exception {
        Account foreignAccount = accountRepository.save(Account.builder()
                .userId(OTHER_USER_ID)
                .name("Foreign")
                .type(AccountType.CHECKING)
                .currency("CHF")
                .initialBalance(BigDecimal.ZERO)
                .build());
        transactionRepository.save(Transaction.builder()
                .userId(OTHER_USER_ID)
                .amount(new BigDecimal("-77777.00"))
                .currency("CHF")
                .date(LocalDate.of(2025, Month.MARCH, 10))
                .description("Foreign expense")
                .account(foreignAccount)
                .source(TransactionSource.MANUAL)
                .build());

        mockMvc.perform(get("/api/v1/analytics/summary")
                        .param("from", FROM).param("to", TO)
                        .with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExpenses", is(2000.00)));
    }
}
