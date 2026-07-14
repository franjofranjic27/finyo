package ch.finyo.account;

import ch.finyo.BaseIntegrationTest;
import ch.finyo.transaction.TransactionRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for POST /api/v1/accounts/bulk: upsert by IBAN (else by
 * normalized name), non-aborting row errors, payload validation (size cap),
 * tenant isolation, the VORSORGE account type and authentication.
 */
class AccountBulkIT extends BaseIntegrationTest {

    private static final String VALID_IBAN = "CH93 0076 2011 6238 5295 7";
    private static final String VALID_IBAN_NORMALIZED = "CH9300762011623852957";
    private static final String INVALID_CHECKSUM_IBAN = "CH9300762011623852958";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void cleanTables() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    private Map<String, Object> item(String name, String type) {
        return Map.of("name", name, "type", type, "currency", "CHF");
    }

    private Map<String, Object> itemWithIban(String name, String type, String iban) {
        return Map.of("name", name, "type", type, "currency", "CHF", "iban", iban);
    }

    @SafeVarargs
    private String bulkBody(Map<String, Object>... items) {
        return objectMapper.writeValueAsString(Map.of("items", List.of(items)));
    }

    @Test
    void mixed_batch_reports_create_and_update_counts() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/bulk").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkBody(
                                itemWithIban("Salary Account", "CHECKING", VALID_IBAN),
                                item("Cash Box", "CASH"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(2)))
                .andExpect(jsonPath("$.updated", is(0)))
                .andExpect(jsonPath("$.failed", is(0)))
                .andExpect(jsonPath("$.errors.length()", is(0)));

        // second import: existing IBAN + existing name update, one new row
        mockMvc.perform(post("/api/v1/accounts/bulk").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkBody(
                                itemWithIban("Salary Account", "CHECKING", VALID_IBAN_NORMALIZED),
                                item("cash box", "CASH"),
                                item("Emergency Fund", "SAVINGS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(1)))
                .andExpect(jsonPath("$.updated", is(2)))
                .andExpect(jsonPath("$.failed", is(0)));

        mockMvc.perform(get("/api/v1/accounts").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(3)));
    }

    @Test
    void iban_match_renames_the_account_instead_of_creating_a_duplicate() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/bulk").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkBody(itemWithIban("Old Name", "CHECKING", VALID_IBAN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(1)));

        mockMvc.perform(post("/api/v1/accounts/bulk").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkBody(itemWithIban("New Name", "CHECKING", "ch93 0076 2011 6238 5295 7"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(0)))
                .andExpect(jsonPath("$.updated", is(1)));

        mockMvc.perform(get("/api/v1/accounts").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].name", is("New Name")))
                .andExpect(jsonPath("$[0].iban", is(VALID_IBAN_NORMALIZED)));
    }

    @Test
    void iban_less_items_fall_back_to_normalized_name_matching() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/bulk").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkBody(item("Savings", "SAVINGS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(1)));

        // different casing/whitespace still matches the same account
        mockMvc.perform(post("/api/v1/accounts/bulk").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkBody(item("  SAVINGS ", "SAVINGS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(0)))
                .andExpect(jsonPath("$.updated", is(1)));

        mockMvc.perform(get("/api/v1/accounts").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)));
    }

    @Test
    void invalid_iban_row_is_reported_without_aborting_the_batch() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/bulk").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkBody(
                                item("Good Account", "CHECKING"),
                                itemWithIban("Broken IBAN", "CHECKING", INVALID_CHECKSUM_IBAN),
                                item("Another Good One", "SAVINGS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(2)))
                .andExpect(jsonPath("$.updated", is(0)))
                .andExpect(jsonPath("$.failed", is(1)))
                .andExpect(jsonPath("$.errors.length()", is(1)))
                .andExpect(jsonPath("$.errors[0]", is("row 2: Invalid IBAN")));

        mockMvc.perform(get("/api/v1/accounts").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)));
    }

    @Test
    void more_than_200_items_returns_400() throws Exception {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            items.add(item("Account " + i, "CHECKING"));
        }

        mockMvc.perform(post("/api/v1/accounts/bulk").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("items", items))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/accounts").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
    }

    @Test
    void empty_items_list_returns_400() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/bulk").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("items", List.of()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void other_users_accounts_with_identical_iban_and_name_are_not_touched() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/bulk").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkBody(itemWithIban("Shared Name", "CHECKING", VALID_IBAN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(1)));

        // the other user's import must create their own row, not update the owner's
        mockMvc.perform(post("/api/v1/accounts/bulk").with(asOtherUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkBody(itemWithIban("Other Name", "CHECKING", VALID_IBAN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(1)))
                .andExpect(jsonPath("$.updated", is(0)));

        mockMvc.perform(get("/api/v1/accounts").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].name", is("Shared Name")));

        mockMvc.perform(get("/api/v1/accounts").with(asOtherUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].name", is("Other Name")));
    }

    @Test
    void vorsorge_type_is_accepted_end_to_end() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/bulk").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkBody(item("Saeule 3a", "VORSORGE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(1)))
                .andExpect(jsonPath("$.failed", is(0)));

        mockMvc.perform(get("/api/v1/accounts").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type", is("VORSORGE")));

        assertThat(accountRepository.findByUserIdOrderByNameAsc(TEST_USER_ID))
                .singleElement()
                .satisfies(account -> assertThat(account.getType()).isEqualTo(AccountType.VORSORGE));
    }

    @Test
    void unauthenticated_request_returns_401() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkBody(item("Main", "CHECKING"))))
                .andExpect(status().isUnauthorized());
    }
}
