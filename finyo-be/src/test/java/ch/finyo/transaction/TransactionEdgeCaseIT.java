package ch.finyo.transaction;

import ch.finyo.BaseIntegrationTest;
import ch.finyo.account.AccountRepository;
import ch.finyo.account.AccountType;
import ch.finyo.account.Account;
import ch.finyo.category.Category;
import ch.finyo.category.CategoryRepository;
import ch.finyo.category.CategoryType;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Adversarial edge-case integration tests for Transaction management.
 *
 * The primary concern is MULTI-TENANT ISOLATION: every database query in
 * TransactionService, AccountService and CategoryService is scoped by userId.
 * These tests verify that the userId scoping is actually enforced end-to-end
 * through the HTTP layer — not just that the service methods look correct in
 * isolation.
 *
 * Test data is set up directly through Spring Data repositories (bypassing the
 * HTTP layer for setup) so that each test controls the exact database state and
 * is independent of the controller's creation logic.
 *
 * Naming convention: <scenario>_<expected_result>
 */
class TransactionEdgeCaseIT extends BaseIntegrationTest {

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

    // Accounts owned by the two distinct users — created fresh before each test
    private Account testUserAccount;
    private Account otherUserAccount;

    @BeforeEach
    void setUpAccounts() {
        transactionRepository.deleteAll();
        categoryRepository.deleteAll();
        accountRepository.deleteAll();

        testUserAccount = accountRepository.save(Account.builder()
            .userId(TEST_USER_ID)
            .name("Test User Checking")
            .type(AccountType.CHECKING)
            .currency("CHF")
            .initialBalance(BigDecimal.ZERO)
            .build());

        otherUserAccount = accountRepository.save(Account.builder()
            .userId(OTHER_USER_ID)
            .name("Other User Checking")
            .type(AccountType.CHECKING)
            .currency("CHF")
            .initialBalance(BigDecimal.ZERO)
            .build());
    }

    // =========================================================================
    // SECTION 1 — MULTI-TENANT ISOLATION (the most dangerous class of bugs)
    // =========================================================================

    /**
     * User B must not be able to read User A's transaction by guessing its UUID.
     *
     * If TransactionService.getById() were missing the userId filter and called
     * findById() instead of findByIdAndUserId(), this test would return 200
     * instead of 404 — a data-leak vulnerability.
     */
    @Test
    void user_cannot_read_another_users_transaction_by_id() throws Exception {
        // Arrange: TEST_USER creates a transaction
        Transaction t = transactionRepository.save(Transaction.builder()
            .userId(TEST_USER_ID)
            .amount(new BigDecimal("-42.00"))
            .currency("CHF")
            .date(LocalDate.now())
            .account(testUserAccount)
            .source(TransactionSource.MANUAL)
            .build());

        // Act & Assert: OTHER_USER tries to GET it — must receive 404, not 200
        mockMvc.perform(get("/api/v1/transactions/{id}", t.getId()).with(asOtherUser()))
            .andExpect(status().isNotFound());
    }

    /**
     * User B must not be able to delete User A's transaction.
     *
     * If TransactionService.delete() called deleteById() without first verifying
     * ownership, it would silently delete User A's data.
     */
    @Test
    void user_cannot_delete_another_users_transaction() throws Exception {
        Transaction t = transactionRepository.save(Transaction.builder()
            .userId(TEST_USER_ID)
            .amount(new BigDecimal("-10.00"))
            .currency("CHF")
            .date(LocalDate.now())
            .account(testUserAccount)
            .source(TransactionSource.MANUAL)
            .build());

        mockMvc.perform(delete("/api/v1/transactions/{id}", t.getId()).with(asOtherUser()))
            .andExpect(status().isNotFound());

        // Verify the transaction is still in the database — it was NOT deleted
        assertThat(transactionRepository.findById(t.getId())).isPresent();
    }

    /**
     * User B must not be able to create a transaction referencing User A's account.
     *
     * If TransactionService.create() used accountRepository.findById() instead of
     * findByIdAndUserId(), User B could book entries into User A's account.
     */
    @Test
    void user_cannot_create_transaction_using_another_users_account() throws Exception {
        // OTHER_USER tries to create a transaction with TEST_USER's account ID
        String body = objectMapper.writeValueAsString(Map.of(
            "amount", -25.00,
            "currency", "CHF",
            "date", "2025-06-01",
            "accountId", testUserAccount.getId().toString()   // owned by TEST_USER
        ));

        mockMvc.perform(post("/api/v1/transactions").with(asOtherUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isNotFound());   // account not found for OTHER_USER
    }

    /**
     * User B must not be able to assign User A's category to a transaction.
     */
    @Test
    void user_cannot_create_transaction_with_another_users_category() throws Exception {
        Category testUserCategory = categoryRepository.save(Category.builder()
            .userId(TEST_USER_ID)
            .name("Groceries")
            .type(CategoryType.EXPENSE)
            .build());

        String body = objectMapper.writeValueAsString(Map.of(
            "amount", -15.50,
            "currency", "CHF",
            "date", "2025-06-01",
            "accountId", otherUserAccount.getId().toString(),
            "categoryId", testUserCategory.getId().toString()  // owned by TEST_USER
        ));

        mockMvc.perform(post("/api/v1/transactions").with(asOtherUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isNotFound());   // category not found for OTHER_USER
    }

    /**
     * The transaction list endpoint must never return another user's transactions,
     * even when both users have transactions in the database.
     */
    @Test
    void list_transactions_only_returns_transactions_belonging_to_the_authenticated_user() throws Exception {
        // Arrange: one transaction per user
        transactionRepository.save(Transaction.builder()
            .userId(TEST_USER_ID)
            .amount(new BigDecimal("100.00"))
            .currency("CHF")
            .date(LocalDate.now())
            .account(testUserAccount)
            .source(TransactionSource.MANUAL)
            .build());

        transactionRepository.save(Transaction.builder()
            .userId(OTHER_USER_ID)
            .amount(new BigDecimal("-999.00"))
            .currency("CHF")
            .date(LocalDate.now())
            .account(otherUserAccount)
            .source(TransactionSource.MANUAL)
            .build());

        // Act: TEST_USER fetches their list — should see exactly 1 entry
        mockMvc.perform(get("/api/v1/transactions").with(asUser()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", is(1)))
            .andExpect(jsonPath("$.content[0].amount", is(100.00)));
    }

    // =========================================================================
    // SECTION 2 — AMOUNT EDGE CASES
    // =========================================================================

    /**
     * A negative amount represents an expense and must be accepted.
     */
    @Test
    void transaction_with_negative_amount_is_accepted_as_expense() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "amount", -123.45,
            "currency", "CHF",
            "date", "2025-01-15",
            "accountId", testUserAccount.getId().toString()
        ));

        mockMvc.perform(post("/api/v1/transactions").with(asUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.amount", is(-123.45)));
    }

    /**
     * A positive amount represents income and must be accepted.
     */
    @Test
    void transaction_with_positive_amount_is_accepted_as_income() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "amount", 3000.00,
            "currency", "CHF",
            "date", "2025-01-25",
            "accountId", testUserAccount.getId().toString()
        ));

        mockMvc.perform(post("/api/v1/transactions").with(asUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.amount", is(3000.00)));
    }

    /**
     * Amount = 0 is currently accepted by @NotNull (zero is not null).
     *
     * OPEN QUESTION for the team: should zero amounts be rejected?
     * A CHF 0.00 transaction has no financial meaning and likely indicates a
     * data entry error or a bug in an import routine. Until a business rule is
     * defined this test documents the CURRENT behaviour (accepted).
     *
     * When a decision is made, change the expected status to isUnprocessableEntity()
     * or isBadRequest() and add a @DecimalMin("0.01") / @DecimalMax("-0.01")
     * constraint with a custom validator (since the sign depends on transaction type).
     */
    @Test
    void transaction_with_zero_amount_is_currently_accepted_REVIEW_THIS() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "amount", 0,
            "currency", "CHF",
            "date", "2025-01-01",
            "accountId", testUserAccount.getId().toString()
        ));

        // CURRENT behaviour: 201 Created. Change this test when a zero-amount
        // validation rule is added.
        mockMvc.perform(post("/api/v1/transactions").with(asUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated());
    }

    /**
     * Amount with 4 decimal places (the column precision) must be stored exactly.
     *
     * The DB column is NUMERIC(19,4). Storing 9.1234 must round-trip correctly.
     */
    @Test
    void transaction_amount_with_four_decimal_places_is_stored_precisely() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "amount", -9.1234,
            "currency", "CHF",
            "date", "2025-03-10",
            "accountId", testUserAccount.getId().toString()
        ));

        String response = mockMvc.perform(post("/api/v1/transactions").with(asUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        // Extract the created ID and re-read the transaction to verify precision
        UUID createdId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        Transaction saved = transactionRepository.findById(createdId).orElseThrow();

        assertThat(saved.getAmount())
            .as("Amount with 4 decimal places must be stored without rounding")
            .isEqualByComparingTo(new BigDecimal("-9.1234"));
    }

    // =========================================================================
    // SECTION 3 — DATE EDGE CASES
    // =========================================================================

    /**
     * A future date must be accepted (budget planning and scheduled transactions).
     */
    @Test
    void transaction_with_future_date_is_accepted() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "amount", -50.00,
            "currency", "CHF",
            "date", LocalDate.now().plusYears(1).toString(),
            "accountId", testUserAccount.getId().toString()
        ));

        mockMvc.perform(post("/api/v1/transactions").with(asUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated());
    }

    /**
     * A very old date must be accepted (historical data import from bank statements).
     */
    @Test
    void transaction_with_very_old_date_is_accepted_for_historical_import() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "amount", -200.00,
            "currency", "CHF",
            "date", "1990-01-01",
            "accountId", testUserAccount.getId().toString()
        ));

        mockMvc.perform(post("/api/v1/transactions").with(asUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated());
    }

    // =========================================================================
    // SECTION 4 — DESCRIPTION EDGE CASES
    // =========================================================================

    /**
     * Description is nullable at the database level — omitting it must succeed.
     */
    @Test
    void transaction_with_null_description_is_accepted() throws Exception {
        // Omit "description" from the JSON entirely
        String body = objectMapper.writeValueAsString(Map.of(
            "amount", -10.00,
            "currency", "CHF",
            "date", "2025-06-01",
            "accountId", testUserAccount.getId().toString()
        ));

        mockMvc.perform(post("/api/v1/transactions").with(asUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.description").doesNotExist());
    }

    /**
     * There is currently NO length constraint on the description column (TEXT type).
     *
     * RISK: a caller could send a multi-megabyte description and blow up memory
     * or trigger a PostgreSQL timeout. Consider adding @Size(max = 1000) or a
     * database-level CHECK constraint.
     *
     * This test documents the CURRENT behaviour (long descriptions are accepted).
     * It acts as a regression alert: if a max-length constraint is added later,
     * this test will correctly start failing, signalling that the spec has changed.
     */
    @Test
    void transaction_with_very_long_description_is_accepted_but_SHOULD_HAVE_MAX_LENGTH() throws Exception {
        String longDescription = "x".repeat(10_000);

        String body = "{" +
            "\"amount\": -1.00," +
            "\"currency\": \"CHF\"," +
            "\"date\": \"2025-06-01\"," +
            "\"accountId\": \"" + testUserAccount.getId() + "\"," +
            "\"description\": \"" + longDescription + "\"" +
            "}";

        // CURRENT behaviour: 201. Add @Size(max = N) to TransactionRequest.description
        // and flip this expectation to isUnprocessableEntity() / isBadRequest() when done.
        mockMvc.perform(post("/api/v1/transactions").with(asUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated());
    }

    // =========================================================================
    // SECTION 5 — REQUIRED FIELD VALIDATION
    // =========================================================================

    @Test
    void transaction_missing_amount_returns_400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "currency", "CHF",
            "date", "2025-06-01",
            "accountId", testUserAccount.getId().toString()
        ));

        mockMvc.perform(post("/api/v1/transactions").with(asUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    void transaction_missing_date_returns_400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "amount", -10.00,
            "currency", "CHF",
            "accountId", testUserAccount.getId().toString()
        ));

        mockMvc.perform(post("/api/v1/transactions").with(asUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    void transaction_missing_account_id_returns_400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "amount", -10.00,
            "currency", "CHF",
            "date", "2025-06-01"
        ));

        mockMvc.perform(post("/api/v1/transactions").with(asUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // SECTION 6 — CURRENCY DEFAULTING
    // =========================================================================

    /**
     * When currency is omitted, the service defaults to "CHF".
     */
    @Test
    void transaction_without_currency_defaults_to_chf() throws Exception {
        // Do not include "currency" in the request body
        String body = "{" +
            "\"amount\": -5.00," +
            "\"date\": \"2025-06-01\"," +
            "\"accountId\": \"" + testUserAccount.getId() + "\"" +
            "}";

        mockMvc.perform(post("/api/v1/transactions").with(asUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.currency", is("CHF")));
    }

    /**
     * When an explicit currency is provided, it must not be overridden.
     */
    @Test
    void transaction_with_explicit_currency_is_stored_as_provided() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "amount", -5.00,
            "currency", "EUR",
            "date", "2025-06-01",
            "accountId", testUserAccount.getId().toString()
        ));

        mockMvc.perform(post("/api/v1/transactions").with(asUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.currency", is("EUR")));
    }

    // =========================================================================
    // SECTION 7 — NON-EXISTENT RESOURCE IDs
    // =========================================================================

    @Test
    void fetching_completely_unknown_transaction_id_returns_404() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/transactions/{id}", nonExistentId).with(asUser()))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleting_completely_unknown_transaction_id_returns_404() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/transactions/{id}", nonExistentId).with(asUser()))
            .andExpect(status().isNotFound());
    }

    @Test
    void creating_transaction_with_nonexistent_account_id_returns_404() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "amount", -20.00,
            "currency", "CHF",
            "date", "2025-06-01",
            "accountId", UUID.randomUUID().toString()
        ));

        mockMvc.perform(post("/api/v1/transactions").with(asUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isNotFound());
    }

    @Test
    void creating_transaction_with_nonexistent_category_id_returns_404() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "amount", -20.00,
            "currency", "CHF",
            "date", "2025-06-01",
            "accountId", testUserAccount.getId().toString(),
            "categoryId", UUID.randomUUID().toString()
        ));

        mockMvc.perform(post("/api/v1/transactions").with(asUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isNotFound());
    }

    // =========================================================================
    // SECTION 8 — DATE RANGE FILTERING
    // =========================================================================

    /**
     * When from and to are provided, only transactions in that range are returned.
     */
    @Test
    void date_range_filter_excludes_transactions_outside_the_range() throws Exception {
        // inside the range
        transactionRepository.save(Transaction.builder()
            .userId(TEST_USER_ID)
            .amount(new BigDecimal("-10.00"))
            .currency("CHF")
            .date(LocalDate.of(2025, 3, 15))
            .account(testUserAccount)
            .source(TransactionSource.MANUAL)
            .build());

        // outside the range (before from)
        transactionRepository.save(Transaction.builder()
            .userId(TEST_USER_ID)
            .amount(new BigDecimal("-20.00"))
            .currency("CHF")
            .date(LocalDate.of(2025, 1, 1))
            .account(testUserAccount)
            .source(TransactionSource.MANUAL)
            .build());

        mockMvc.perform(get("/api/v1/transactions")
                .with(asUser())
                .param("from", "2025-03-01")
                .param("to", "2025-03-31"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", is(1)));
    }

    /**
     * When only "from" is provided without "to", the service falls back to no
     * date filter (both must be non-null to activate the date filter).
     *
     * RISK: partial parameters silently ignore the filter. Consider rejecting
     * requests where exactly one of from/to is provided.
     */
    @Test
    void providing_only_from_without_to_returns_all_transactions_REVIEW_THIS() throws Exception {
        transactionRepository.save(Transaction.builder()
            .userId(TEST_USER_ID)
            .amount(new BigDecimal("-5.00"))
            .currency("CHF")
            .date(LocalDate.of(2020, 1, 1))
            .account(testUserAccount)
            .source(TransactionSource.MANUAL)
            .build());

        transactionRepository.save(Transaction.builder()
            .userId(TEST_USER_ID)
            .amount(new BigDecimal("-6.00"))
            .currency("CHF")
            .date(LocalDate.of(2025, 6, 1))
            .account(testUserAccount)
            .source(TransactionSource.MANUAL)
            .build());

        // Only "from" is set — the service will ignore it because "to" is null.
        // Both transactions should be returned.
        mockMvc.perform(get("/api/v1/transactions")
                .with(asUser())
                .param("from", "2025-01-01"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", is(2)));
    }
}
