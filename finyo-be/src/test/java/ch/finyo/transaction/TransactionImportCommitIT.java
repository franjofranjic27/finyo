package ch.finyo.transaction;

import ch.finyo.BaseIntegrationTest;
import ch.finyo.account.Account;
import ch.finyo.account.AccountRepository;
import ch.finyo.account.AccountType;
import ch.finyo.category.Category;
import ch.finyo.category.CategoryRepository;
import ch.finyo.category.CategoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for POST /api/v1/transactions/import/commit: persistence
 * of confirmed rows including source, category and external reference,
 * idempotent re-commits via the unique bank reference, tenant isolation for
 * accounts and categories, and authentication.
 */
class TransactionImportCommitIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Account account;
    private Category groceries;

    @BeforeEach
    void seedAccountAndCategory() {
        transactionRepository.deleteAll();
        categoryRepository.deleteAll();
        accountRepository.deleteAll();
        account = accountRepository.save(Account.builder()
                .userId(TEST_USER_ID)
                .name("Commit Account")
                .type(AccountType.CHECKING)
                .currency("CHF")
                .initialBalance(BigDecimal.ZERO)
                .build());
        groceries = categoryRepository.save(Category.builder()
                .userId(TEST_USER_ID)
                .name("Groceries")
                .type(CategoryType.EXPENSE)
                .build());
    }

    private Map<String, Object> row(String date, String amount, String description,
                                    String externalRef, UUID categoryId) {
        Map<String, Object> row = new HashMap<>();
        row.put("date", date);
        row.put("amount", amount);
        row.put("description", description);
        if (externalRef != null) {
            row.put("externalRef", externalRef);
        }
        if (categoryId != null) {
            row.put("categoryId", categoryId.toString());
        }
        return row;
    }

    private String commitBody(UUID accountId, String format, boolean skipDuplicates,
                              List<Map<String, Object>> rows) {
        return objectMapper.writeValueAsString(Map.of(
                "accountId", accountId.toString(),
                "format", format,
                "skipDuplicates", skipDuplicates,
                "rows", rows));
    }

    /** Rows as a camt preview would produce them for a two-entry statement. */
    private List<Map<String, Object>> camtRows() {
        return List.of(
                row("2025-03-15", "-42.50", "Einkauf Lebensmittel", "SYNTH-IT-REF-10", groceries.getId()),
                row("2025-03-25", "2500.00", "Lohn Maerz", "SYNTH-IT-REF-11", null));
    }

    @Test
    void commit_persists_rows_with_source_category_and_external_reference() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/import/commit").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitBody(account.getId(), "CAMT053", true, camtRows())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows", is(2)))
                .andExpect(jsonPath("$.imported", is(2)))
                .andExpect(jsonPath("$.skippedDuplicates", is(0)));

        List<Transaction> saved = transactionRepository.findAll();
        assertThat(saved).hasSize(2);
        Transaction expense = saved.stream()
                .filter(t -> t.getAmount().signum() < 0)
                .findFirst().orElseThrow();
        assertThat(expense.getSource()).isEqualTo(TransactionSource.CAMT_IMPORT);
        assertThat(expense.getExternalRef()).isEqualTo("SYNTH-IT-REF-10");
        assertThat(expense.getCurrency()).isEqualTo("CHF");
        assertThat(expense.getUserId()).isEqualTo(TEST_USER_ID);
        // the id of a lazy proxy is readable without an open session
        assertThat(expense.getCategory().getId()).isEqualTo(groceries.getId());
    }

    @Test
    void recommitting_the_same_camt_rows_skips_all_of_them() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/import/commit").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitBody(account.getId(), "CAMT053", true, camtRows())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported", is(2)));

        mockMvc.perform(post("/api/v1/transactions/import/commit").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitBody(account.getId(), "CAMT053", true, camtRows())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported", is(0)))
                .andExpect(jsonPath("$.skippedDuplicates", is(2)));

        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Test
    void recommitting_a_known_bank_reference_is_skipped_even_without_skip_duplicates() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/import/commit").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitBody(account.getId(), "CAMT053", true, camtRows())))
                .andExpect(status().isOk());

        // opting out of duplicate skipping must not run the batch into the partial unique index
        // on (user_id, external_ref) — that 409 would roll back every row of the commit
        mockMvc.perform(post("/api/v1/transactions/import/commit").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitBody(account.getId(), "CAMT053", false, camtRows())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported", is(0)))
                .andExpect(jsonPath("$.skippedDuplicates", is(2)));

        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Test
    void commit_persists_batch_rows_without_a_reference_that_only_differ_in_amount() throws Exception {
        // sub-transactions of a Sammelbuchung carry no own reference — each one must land
        mockMvc.perform(post("/api/v1/transactions/import/commit").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitBody(account.getId(), "CAMT053", true, List.of(
                                row("2025-06-01", "-25.00", "Lastschrift eins", null, null),
                                row("2025-06-01", "-30.00", "Lastschrift zwei", null, null)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported", is(2)))
                .andExpect(jsonPath("$.skippedDuplicates", is(0)));

        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Test
    void commit_with_a_csv_format_persists_the_csv_import_source() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/import/commit").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitBody(account.getId(), "CSV", true,
                                List.of(row("2025-03-15", "-9.90", "Coffee", null, null)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported", is(1)));

        Transaction saved = transactionRepository.findAll().get(0);
        assertThat(saved.getSource()).isEqualTo(TransactionSource.CSV_IMPORT);
        assertThat(saved.getExternalRef()).isNull();
    }

    @Test
    void commit_with_a_foreign_category_returns_404_and_persists_nothing() throws Exception {
        Category foreignCategory = categoryRepository.save(Category.builder()
                .userId(OTHER_USER_ID)
                .name("Foreign")
                .type(CategoryType.EXPENSE)
                .build());

        mockMvc.perform(post("/api/v1/transactions/import/commit").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitBody(account.getId(), "CAMT053", true,
                                List.of(row("2025-03-15", "-1.00", "sneaky", "SYNTH-IT-REF-12",
                                        foreignCategory.getId())))))
                .andExpect(status().isNotFound());

        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void commit_into_a_foreign_account_returns_404_and_persists_nothing() throws Exception {
        Account foreignAccount = accountRepository.save(Account.builder()
                .userId(OTHER_USER_ID)
                .name("Foreign Account")
                .type(AccountType.CHECKING)
                .currency("CHF")
                .initialBalance(BigDecimal.ZERO)
                .build());

        mockMvc.perform(post("/api/v1/transactions/import/commit").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitBody(foreignAccount.getId(), "CSV", true,
                                List.of(row("2025-03-15", "-1.00", "x", null, null)))))
                .andExpect(status().isNotFound());

        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void commit_without_rows_returns_400() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/import/commit").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitBody(account.getId(), "CSV", true, List.of())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void commit_without_authentication_returns_401() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/import/commit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commitBody(account.getId(), "CSV", true,
                                List.of(row("2025-03-15", "-1.00", "x", null, null)))))
                .andExpect(status().isUnauthorized());
    }
}
