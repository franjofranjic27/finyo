package ch.finyo.savings;

import ch.finyo.BaseIntegrationTest;
import ch.finyo.account.Account;
import ch.finyo.account.AccountRepository;
import ch.finyo.account.AccountType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for /api/v1/savings: lifecycle including archiving and
 * the includeArchived list toggle, plus validation and tenant isolation.
 */
class SavingsGoalIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SavingsGoalRepository savingsGoalRepository;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void cleanTables() {
        savingsGoalRepository.deleteAll();
        accountRepository.deleteAll();
    }

    private String goalBody(String name, int targetAmount) {
        return objectMapper.writeValueAsString(Map.of(
                "name", name,
                "targetAmount", targetAmount,
                "currentAmount", 250));
    }

    @Test
    void full_lifecycle_create_get_update_archive_and_filtered_listing() throws Exception {
        String created = mockMvc.perform(post("/api/v1/savings").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goalBody("Emergency Fund", 1000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.progressPercentage", is(25.0)))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        mockMvc.perform(get("/api/v1/savings/{id}", id).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Emergency Fund")));

        mockMvc.perform(put("/api/v1/savings/{id}", id).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goalBody("Bigger Emergency Fund", 2000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Bigger Emergency Fund")))
                .andExpect(jsonPath("$.targetAmount", is(2000)));

        mockMvc.perform(patch("/api/v1/savings/{id}/archive", id).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived", is(true)));

        // Default listing hides archived goals; includeArchived=true shows them
        mockMvc.perform(get("/api/v1/savings").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
        mockMvc.perform(get("/api/v1/savings").param("includeArchived", "true").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)));

        mockMvc.perform(delete("/api/v1/savings/{id}", id).with(asUser()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/savings/{id}", id).with(asUser()))
                .andExpect(status().isNotFound());
    }

    /**
     * Regression test for the lazy-account mapping: listing and reading a goal
     * linked to an account must resolve the account name outside the request
     * that created it (open-in-view is disabled).
     */
    @Test
    void goal_linked_to_an_account_exposes_the_account_name_when_read_back() throws Exception {
        Account account = accountRepository.save(Account.builder()
                .userId(TEST_USER_ID)
                .name("Savings Account")
                .type(AccountType.SAVINGS)
                .currency("CHF")
                .initialBalance(BigDecimal.ZERO)
                .build());
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Linked Goal",
                "targetAmount", 5000,
                "accountId", account.getId().toString()));

        String created = mockMvc.perform(post("/api/v1/savings").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        mockMvc.perform(get("/api/v1/savings").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountName", is("Savings Account")));
        mockMvc.perform(get("/api/v1/savings/{id}", id).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId", is(account.getId().toString())));
    }

    @Test
    void create_with_zero_target_amount_returns_400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Invalid Goal",
                "targetAmount", 0));

        mockMvc.perform(post("/api/v1/savings").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void other_user_cannot_read_archive_or_delete_a_foreign_goal() throws Exception {
        SavingsGoal owned = savingsGoalRepository.save(SavingsGoal.builder()
                .userId(TEST_USER_ID)
                .name("Owner Goal")
                .targetAmount(new BigDecimal("1000"))
                .currentAmount(BigDecimal.ZERO)
                .archived(false)
                .build());

        mockMvc.perform(get("/api/v1/savings/{id}", owned.getId()).with(asOtherUser()))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/savings/{id}/archive", owned.getId()).with(asOtherUser()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/savings/{id}", owned.getId()).with(asOtherUser()))
                .andExpect(status().isNotFound());
    }
}
