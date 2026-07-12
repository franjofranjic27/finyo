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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for /api/v1/cards: CRUD lifecycle, account linking
 * (including the ON DELETE SET NULL detach), cross-tenant isolation and
 * authentication.
 */
class PaymentCardIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentCardRepository paymentCardRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void cleanTables() {
        transactionRepository.deleteAll();
        paymentCardRepository.deleteAll();
        accountRepository.deleteAll();
    }

    private Account persistAccount(String userId, String name) {
        return accountRepository.save(Account.builder()
                .userId(userId)
                .name(name)
                .type(AccountType.CHECKING)
                .currency("CHF")
                .initialBalance(BigDecimal.ZERO)
                .build());
    }

    private String cardBody(String name, UUID accountId) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("provider", "Mastercard");
        body.put("currency", "CHF");
        body.put("feeNote", "CHF 0 / Jahr");
        if (accountId != null) {
            body.put("accountId", accountId.toString());
        }
        return objectMapper.writeValueAsString(body);
    }

    @Test
    void full_lifecycle_create_list_update_delete() throws Exception {
        String created = mockMvc.perform(post("/api/v1/cards").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardBody("Debit Mastercard", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Debit Mastercard")))
                .andExpect(jsonPath("$.provider", is("Mastercard")))
                .andExpect(jsonPath("$.accountId", nullValue()))
                .andExpect(jsonPath("$.accountName", nullValue()))
                .andExpect(jsonPath("$.scope", is("PRIVATE")))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        mockMvc.perform(get("/api/v1/cards").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].name", is("Debit Mastercard")));

        String updateBody = objectMapper.writeValueAsString(Map.of(
                "name", "Amex Business",
                "provider", "American Express",
                "scope", "BUSINESS"));
        mockMvc.perform(put("/api/v1/cards/{id}", id).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Amex Business")))
                .andExpect(jsonPath("$.scope", is("BUSINESS")));

        mockMvc.perform(delete("/api/v1/cards/{id}", id).with(asUser()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/cards").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
    }

    @Test
    void list_is_ordered_by_name() throws Exception {
        mockMvc.perform(post("/api/v1/cards").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardBody("Zeta Card", null)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/cards").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardBody("Alpha Card", null)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/cards").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name", is("Alpha Card")))
                .andExpect(jsonPath("$[1].name", is("Zeta Card")));
    }

    @Test
    void create_with_own_account_resolves_accountName() throws Exception {
        Account account = persistAccount(TEST_USER_ID, "Salary Account");

        mockMvc.perform(post("/api/v1/cards").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardBody("Debit Mastercard", account.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId", is(account.getId().toString())))
                .andExpect(jsonPath("$.accountName", is("Salary Account")));

        mockMvc.perform(get("/api/v1/cards").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountName", is("Salary Account")));
    }

    @Test
    void create_linked_to_foreign_account_is_rejected_with_400() throws Exception {
        Account foreign = persistAccount(OTHER_USER_ID, "Foreign Account");

        mockMvc.perform(post("/api/v1/cards").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardBody("Sneaky Card", foreign.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", is("Unknown account")));
    }

    @Test
    void create_linked_to_unknown_account_is_rejected_with_400() throws Exception {
        mockMvc.perform(post("/api/v1/cards").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardBody("Orphan Card", UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", is("Unknown account")));
    }

    @Test
    void create_without_name_returns_400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("provider", "Visa"));

        mockMvc.perform(post("/api/v1/cards").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleting_the_linked_account_detaches_the_card_instead_of_deleting_it() throws Exception {
        Account account = persistAccount(TEST_USER_ID, "Doomed Account");

        mockMvc.perform(post("/api/v1/cards").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardBody("Survivor Card", account.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/accounts/{id}", account.getId()).with(asUser()))
                .andExpect(status().isNoContent());

        // ON DELETE SET NULL: the card survives with a detached account link.
        mockMvc.perform(get("/api/v1/cards").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].name", is("Survivor Card")))
                .andExpect(jsonPath("$[0].accountId", nullValue()))
                .andExpect(jsonPath("$[0].accountName", nullValue()));
    }

    @Test
    void other_user_cannot_see_update_or_delete_a_foreign_card() throws Exception {
        String created = mockMvc.perform(post("/api/v1/cards").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardBody("Owner Card", null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        mockMvc.perform(get("/api/v1/cards").with(asOtherUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));

        mockMvc.perform(put("/api/v1/cards/{id}", id).with(asOtherUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardBody("Hijacked", null)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/cards/{id}", id).with(asOtherUser()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/cards").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)));
    }

    @Test
    void unauthenticated_requests_are_rejected_with_401() throws Exception {
        mockMvc.perform(get("/api/v1/cards"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cardBody("No Auth Card", null)))
                .andExpect(status().isUnauthorized());
    }
}
