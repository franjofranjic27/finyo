package ch.finyo.budget;

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
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for /api/v1/budgets: lifecycle, the month-to-date
 * /status aggregation against real transactions, and tenant isolation.
 */
class BudgetIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Category groceries;

    @BeforeEach
    void seedCategory() {
        transactionRepository.deleteAll();
        budgetRepository.deleteAll();
        categoryRepository.deleteAll();
        accountRepository.deleteAll();
        groceries = categoryRepository.save(Category.builder()
                .userId(TEST_USER_ID)
                .name("Groceries")
                .type(CategoryType.EXPENSE)
                .build());
    }

    private String budgetBody(UUID categoryId, int amount) {
        return objectMapper.writeValueAsString(Map.of(
                "categoryId", categoryId.toString(),
                "amount", amount,
                "period", "MONTHLY",
                "validFrom", LocalDate.now(SwissTime.ZONE).withDayOfMonth(1).toString()));
    }

    @Test
    void full_lifecycle_create_list_get_update_delete() throws Exception {
        String created = mockMvc.perform(post("/api/v1/budgets").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(budgetBody(groceries.getId(), 600)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryName", is("Groceries")))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        mockMvc.perform(get("/api/v1/budgets").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)));

        String fetched = mockMvc.perform(get("/api/v1/budgets/{id}", id).with(asUser()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(new BigDecimal(objectMapper.readTree(fetched).get("amount").asText()))
                .isEqualByComparingTo("600");

        String updated = mockMvc.perform(put("/api/v1/budgets/{id}", id).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(budgetBody(groceries.getId(), 750)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(new BigDecimal(objectMapper.readTree(updated).get("amount").asText()))
                .isEqualByComparingTo("750");

        mockMvc.perform(delete("/api/v1/budgets/{id}", id).with(asUser()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/budgets/{id}", id).with(asUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    void status_reflects_month_to_date_spending_of_the_budgeted_category() throws Exception {
        mockMvc.perform(post("/api/v1/budgets").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(budgetBody(groceries.getId(), 600)))
                .andExpect(status().isCreated());

        Account account = accountRepository.save(Account.builder()
                .userId(TEST_USER_ID)
                .name("Checking")
                .type(AccountType.CHECKING)
                .currency("CHF")
                .initialBalance(BigDecimal.ZERO)
                .build());
        transactionRepository.save(Transaction.builder()
                .userId(TEST_USER_ID)
                .amount(new BigDecimal("-150.00"))
                .currency("CHF")
                .date(LocalDate.now(SwissTime.ZONE).withDayOfMonth(1))
                .description("Groceries this month")
                .category(groceries)
                .account(account)
                .source(TransactionSource.MANUAL)
                .build());

        mockMvc.perform(get("/api/v1/budgets/status").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].spent", is(150.00)))
                .andExpect(jsonPath("$[0].remaining", is(450.00)))
                .andExpect(jsonPath("$[0].overBudget", is(false)));
    }

    @Test
    void create_with_a_foreign_category_returns_404() throws Exception {
        Category foreignCategory = categoryRepository.save(Category.builder()
                .userId(OTHER_USER_ID)
                .name("Foreign")
                .type(CategoryType.EXPENSE)
                .build());

        mockMvc.perform(post("/api/v1/budgets").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(budgetBody(foreignCategory.getId(), 100)))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_without_amount_returns_400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "categoryId", groceries.getId().toString(),
                "validFrom", "2025-01-01"));

        mockMvc.perform(post("/api/v1/budgets").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
