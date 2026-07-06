package ch.finyo.account;

import ch.finyo.BaseIntegrationTest;
import ch.finyo.transaction.TransactionRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for /api/v1/accounts covering the full HTTP lifecycle,
 * bean-validation failures and cross-tenant isolation.
 */
class AccountIT extends BaseIntegrationTest {

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

    private String accountBody(String name) {
        return objectMapper.writeValueAsString(Map.of(
                "name", name,
                "type", "CHECKING",
                "currency", "CHF",
                "initialBalance", 1000));
    }

    @Test
    void full_lifecycle_create_list_get_update_delete() throws Exception {
        String created = mockMvc.perform(post("/api/v1/accounts").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountBody("Main Checking")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Main Checking")))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        mockMvc.perform(get("/api/v1/accounts").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)));

        mockMvc.perform(get("/api/v1/accounts/{id}", id).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Main Checking")));

        mockMvc.perform(put("/api/v1/accounts/{id}", id).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountBody("Renamed Checking")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Renamed Checking")));

        mockMvc.perform(delete("/api/v1/accounts/{id}", id).with(asUser()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/accounts/{id}", id).with(asUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_without_name_returns_400_problem_detail() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "type", "CHECKING",
                "currency", "CHF"));

        mockMvc.perform(post("/api/v1/accounts").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void other_user_cannot_read_or_delete_a_foreign_account() throws Exception {
        Account owned = accountRepository.save(Account.builder()
                .userId(TEST_USER_ID)
                .name("Owner Account")
                .type(AccountType.SAVINGS)
                .currency("CHF")
                .initialBalance(BigDecimal.ZERO)
                .build());

        mockMvc.perform(get("/api/v1/accounts/{id}", owned.getId()).with(asOtherUser()))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/accounts/{id}", owned.getId()).with(asOtherUser()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/accounts").with(asOtherUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
    }
}
