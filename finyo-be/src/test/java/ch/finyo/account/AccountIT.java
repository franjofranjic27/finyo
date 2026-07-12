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
    void create_with_iban_stores_it_normalized_and_defaults_scope_and_toClose() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Salary Account",
                "type", "CHECKING",
                "currency", "CHF",
                "iban", "ch93 0076 2011 6238 5295 7",
                "bic", "pofichbexxx",
                "contractNumber", "K-987",
                "feeNote", "CHF 5 / Monat"));

        mockMvc.perform(post("/api/v1/accounts").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.iban", is("CH9300762011623852957")))
                .andExpect(jsonPath("$.bic", is("POFICHBEXXX")))
                .andExpect(jsonPath("$.contractNumber", is("K-987")))
                .andExpect(jsonPath("$.feeNote", is("CHF 5 / Monat")))
                .andExpect(jsonPath("$.scope", is("PRIVATE")))
                .andExpect(jsonPath("$.toClose", is(false)));
    }

    @Test
    void create_with_invalid_iban_checksum_returns_400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Broken IBAN",
                "type", "CHECKING",
                "currency", "CHF",
                "iban", "CH9300762011623852958"));

        mockMvc.perform(post("/api/v1/accounts").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", is("Invalid IBAN")));
    }

    @Test
    void create_with_invalid_bic_returns_400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Broken BIC",
                "type", "CHECKING",
                "currency", "CHF",
                "bic", "12INVALID"));

        mockMvc.perform(post("/api/v1/accounts").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", is("Invalid BIC")));
    }

    @Test
    void update_replaces_master_data_fields() throws Exception {
        String created = mockMvc.perform(post("/api/v1/accounts").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountBody("Business Account")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        String updateBody = objectMapper.writeValueAsString(Map.of(
                "name", "Business Account",
                "type", "CHECKING",
                "currency", "CHF",
                "iban", "de89 3704 0044 0532 0130 00",
                "scope", "BUSINESS",
                "toClose", true));

        mockMvc.perform(put("/api/v1/accounts/{id}", id).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iban", is("DE89370400440532013000")))
                .andExpect(jsonPath("$.scope", is("BUSINESS")))
                .andExpect(jsonPath("$.toClose", is(true)));
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
