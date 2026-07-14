package ch.finyo.budget;

import ch.finyo.BaseIntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for /api/v1/monthly-budget: default view, PUT upsert of the
 * net income, position CRUD lifecycle, duplicate-name handling, integration of
 * fixed costs into fixedCostsPerMonth and available, tenant isolation and
 * authentication.
 */
class MonthlyBudgetIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MonthlyBudgetRepository monthlyBudgetRepository;

    @Autowired
    private MonthlyBudgetPositionRepository positionRepository;

    @Autowired
    private FixedCostRepository fixedCostRepository;

    @BeforeEach
    void cleanUp() {
        monthlyBudgetRepository.deleteAll();
        positionRepository.deleteAll();
        fixedCostRepository.deleteAll();
    }

    private String netIncomeBody(String netIncome) {
        return objectMapper.writeValueAsString(Map.of("netIncome", netIncome));
    }

    private String positionBody(String name, String amount) {
        return objectMapper.writeValueAsString(Map.of("name", name, "amount", amount));
    }

    private void saveMonthlyFixedCost(String userId, String name, String amount) {
        fixedCostRepository.save(FixedCost.builder()
                .userId(userId)
                .name(name)
                .paymentInterval(PaymentInterval.MONTHLY)
                .amount(new BigDecimal(amount))
                .build());
    }

    private JsonNode getBudget(JwtRequestPostProcessor user) throws Exception {
        String body = mockMvc.perform(get("/api/v1/monthly-budget").with(user))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode createPosition(JwtRequestPostProcessor user, String name, String amount) throws Exception {
        String body = mockMvc.perform(post("/api/v1/monthly-budget/positions").with(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(positionBody(name, amount)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private BigDecimal decimal(JsonNode node, String field) {
        return new BigDecimal(node.get(field).asText());
    }

    @Test
    void get_returns_the_default_view_when_no_budget_and_no_fixed_costs_exist() throws Exception {
        JsonNode json = getBudget(asUser());

        assertThat(decimal(json, "netIncome")).isEqualByComparingTo("0");
        assertThat(json.get("positions").isEmpty()).isTrue();
        assertThat(decimal(json, "fixedCostsPerMonth")).isEqualByComparingTo("0");
        assertThat(decimal(json, "available")).isEqualByComparingTo("0");
    }

    @Test
    void put_upserts_the_net_income_and_get_integrates_positions_and_fixed_costs() throws Exception {
        saveMonthlyFixedCost(TEST_USER_ID, "Rent", "100");
        createPosition(asUser(), "Sparen", "500");
        createPosition(asUser(), "Säule 3a", "588");

        String putResponse = mockMvc.perform(put("/api/v1/monthly-budget").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(netIncomeBody("6000")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode putJson = objectMapper.readTree(putResponse);
        assertThat(putJson.get("positions").size()).isEqualTo(2);
        assertThat(decimal(putJson, "fixedCostsPerMonth")).isEqualByComparingTo("100.00");
        // 6000 - 500 - 588 - 100 = 4812
        assertThat(decimal(putJson, "available")).isEqualByComparingTo("4812.00");

        JsonNode getJson = getBudget(asUser());
        assertThat(decimal(getJson, "netIncome")).isEqualByComparingTo("6000");
        assertThat(decimal(getJson, "available")).isEqualByComparingTo("4812.00");

        // second PUT updates the same row instead of creating another one
        String secondPut = mockMvc.perform(put("/api/v1/monthly-budget").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(netIncomeBody("6500")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(decimal(objectMapper.readTree(secondPut), "available")).isEqualByComparingTo("5312.00");
        assertThat(monthlyBudgetRepository.count()).isEqualTo(1);
    }

    @Test
    void position_crud_lifecycle_creates_updates_and_deletes_a_position() throws Exception {
        // create: sort order is assigned sequentially, response is the aggregate
        JsonNode created = createPosition(asUser(), "Sparen", "500");
        createPosition(asUser(), "Investieren", "400");
        JsonNode firstPosition = created.get("positions").get(0);
        assertThat(firstPosition.get("name").asText()).isEqualTo("Sparen");
        assertThat(firstPosition.get("sortOrder").asInt()).isZero();
        assertThat(decimal(created, "available")).isEqualByComparingTo("-500");
        UUID positionId = UUID.fromString(firstPosition.get("id").asText());

        // update: rename and change the amount
        String updateResponse = mockMvc.perform(
                        put("/api/v1/monthly-budget/positions/" + positionId).with(asUser())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(positionBody("Notgroschen", "300")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode updated = objectMapper.readTree(updateResponse);
        assertThat(updated.get("positions").size()).isEqualTo(2);
        assertThat(updated.get("positions").get(0).get("name").asText()).isEqualTo("Notgroschen");
        assertThat(decimal(updated, "available")).isEqualByComparingTo("-700");

        // delete: the aggregate no longer contains the position
        String deleteResponse = mockMvc.perform(
                        delete("/api/v1/monthly-budget/positions/" + positionId).with(asUser()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode afterDelete = objectMapper.readTree(deleteResponse);
        assertThat(afterDelete.get("positions").size()).isEqualTo(1);
        assertThat(afterDelete.get("positions").get(0).get("name").asText()).isEqualTo("Investieren");
        assertThat(decimal(afterDelete, "available")).isEqualByComparingTo("-400");
        assertThat(positionRepository.count()).isEqualTo(1);
    }

    @Test
    void create_position_with_a_duplicate_name_returns_400_case_insensitively() throws Exception {
        createPosition(asUser(), "Sparen", "500");

        mockMvc.perform(post("/api/v1/monthly-budget/positions").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(positionBody("SPAREN", "100")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_position_allows_keeping_the_own_name_but_rejects_another_positions_name() throws Exception {
        JsonNode created = createPosition(asUser(), "Sparen", "500");
        createPosition(asUser(), "Investieren", "400");
        String positionId = created.get("positions").get(0).get("id").asText();

        // renaming to the own name (self) is allowed
        mockMvc.perform(put("/api/v1/monthly-budget/positions/" + positionId).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(positionBody("Sparen", "550")))
                .andExpect(status().isOk());

        // taking another position's name is rejected
        mockMvc.perform(put("/api/v1/monthly-budget/positions/" + positionId).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(positionBody("investieren", "550")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void position_requests_with_invalid_payloads_return_400() throws Exception {
        mockMvc.perform(post("/api/v1/monthly-budget/positions").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(positionBody("", "100")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/monthly-budget/positions").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(positionBody("Sparen", "-1")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/monthly-budget").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void position_operations_on_a_foreign_position_return_404() throws Exception {
        JsonNode created = createPosition(asUser(), "Sparen", "500");
        String positionId = created.get("positions").get(0).get("id").asText();

        mockMvc.perform(put("/api/v1/monthly-budget/positions/" + positionId).with(asOtherUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(positionBody("Hijack", "1")))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/monthly-budget/positions/" + positionId).with(asOtherUser()))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/monthly-budget/positions/" + UUID.randomUUID()).with(asUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    void other_user_gets_the_default_view_not_the_owners_budget_or_positions() throws Exception {
        saveMonthlyFixedCost(TEST_USER_ID, "Rent", "100");
        createPosition(asUser(), "Sparen", "500");
        mockMvc.perform(put("/api/v1/monthly-budget").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(netIncomeBody("6000")))
                .andExpect(status().isOk());

        JsonNode json = getBudget(asOtherUser());
        assertThat(decimal(json, "netIncome")).isEqualByComparingTo("0");
        assertThat(json.get("positions").isEmpty()).isTrue();
        assertThat(decimal(json, "fixedCostsPerMonth")).isEqualByComparingTo("0");
        assertThat(decimal(json, "available")).isEqualByComparingTo("0");

        // two users may use the same position name independently
        mockMvc.perform(post("/api/v1/monthly-budget/positions").with(asOtherUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(positionBody("Sparen", "300")))
                .andExpect(status().isCreated());
    }

    @Test
    void unauthenticated_requests_return_401() throws Exception {
        mockMvc.perform(get("/api/v1/monthly-budget"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/v1/monthly-budget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(netIncomeBody("6000")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/monthly-budget/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(positionBody("Sparen", "100")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/monthly-budget/positions/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
