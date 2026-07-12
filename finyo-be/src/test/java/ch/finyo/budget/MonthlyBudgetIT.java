package ch.finyo.budget;

import ch.finyo.BaseIntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for /api/v1/monthly-budget: default all-zero view,
 * PUT upsert semantics, integration of fixed costs into fixedCostsPerMonth
 * and available, tenant isolation and authentication.
 */
class MonthlyBudgetIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MonthlyBudgetRepository monthlyBudgetRepository;

    @Autowired
    private FixedCostRepository fixedCostRepository;

    @BeforeEach
    void cleanUp() {
        monthlyBudgetRepository.deleteAll();
        fixedCostRepository.deleteAll();
    }

    private String budgetBody(String netIncome, String savings, String investing,
                              String pillar3a, String taxReserve, String workCosts) {
        return objectMapper.writeValueAsString(Map.of(
                "netIncome", netIncome,
                "savings", savings,
                "investing", investing,
                "pillar3a", pillar3a,
                "taxReserve", taxReserve,
                "workCosts", workCosts));
    }

    private void saveMonthlyFixedCost(String userId, String name, String amount) {
        fixedCostRepository.save(FixedCost.builder()
                .userId(userId)
                .name(name)
                .paymentInterval(PaymentInterval.MONTHLY)
                .amount(new BigDecimal(amount))
                .build());
    }

    private JsonNode getBudget(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor user) throws Exception {
        String body = mockMvc.perform(get("/api/v1/monthly-budget").with(user))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private BigDecimal decimal(JsonNode node, String field) {
        return new BigDecimal(node.get(field).asText());
    }

    @Test
    void get_returns_all_zeros_when_no_budget_and_no_fixed_costs_exist() throws Exception {
        JsonNode json = getBudget(asUser());

        for (String field : new String[]{"netIncome", "savings", "investing", "pillar3a",
                "taxReserve", "workCosts", "fixedCostsPerMonth", "available"}) {
            assertThat(decimal(json, field)).as(field).isEqualByComparingTo("0");
        }
    }

    @Test
    void put_upserts_the_budget_and_get_integrates_the_fixed_costs() throws Exception {
        saveMonthlyFixedCost(TEST_USER_ID, "Rent", "100");

        String putResponse = mockMvc.perform(put("/api/v1/monthly-budget").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(budgetBody("6000", "500", "400", "588", "800", "200")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode putJson = objectMapper.readTree(putResponse);
        assertThat(decimal(putJson, "fixedCostsPerMonth")).isEqualByComparingTo("100.00");
        // 6000 - 500 - 400 - 588 - 800 - 200 - 100 = 3412
        assertThat(decimal(putJson, "available")).isEqualByComparingTo("3412.00");

        JsonNode getJson = getBudget(asUser());
        assertThat(decimal(getJson, "netIncome")).isEqualByComparingTo("6000");
        assertThat(decimal(getJson, "pillar3a")).isEqualByComparingTo("588");
        assertThat(decimal(getJson, "fixedCostsPerMonth")).isEqualByComparingTo("100.00");
        assertThat(decimal(getJson, "available")).isEqualByComparingTo("3412.00");

        // second PUT updates the same row instead of creating another one
        String secondPut = mockMvc.perform(put("/api/v1/monthly-budget").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(budgetBody("6500", "500", "400", "588", "800", "200")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(decimal(objectMapper.readTree(secondPut), "available")).isEqualByComparingTo("3912.00");
        assertThat(monthlyBudgetRepository.count()).isEqualTo(1);
    }

    @Test
    void get_returns_negative_available_when_fixed_costs_exceed_the_plan() throws Exception {
        saveMonthlyFixedCost(TEST_USER_ID, "Rent", "100");

        mockMvc.perform(put("/api/v1/monthly-budget").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(budgetBody("0", "0", "0", "0", "0", "0")))
                .andExpect(status().isOk());

        JsonNode json = getBudget(asUser());
        assertThat(decimal(json, "fixedCostsPerMonth")).isEqualByComparingTo("100.00");
        assertThat(decimal(json, "available")).isEqualByComparingTo("-100.00");
    }

    @Test
    void put_with_missing_or_negative_fields_returns_400() throws Exception {
        String missingNetIncome = objectMapper.writeValueAsString(Map.of(
                "savings", "500", "investing", "400", "pillar3a", "588",
                "taxReserve", "800", "workCosts", "200"));
        mockMvc.perform(put("/api/v1/monthly-budget").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingNetIncome))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/monthly-budget").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(budgetBody("6000", "-1", "0", "0", "0", "0")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void other_user_gets_the_default_zero_view_not_the_owners_budget() throws Exception {
        saveMonthlyFixedCost(TEST_USER_ID, "Rent", "100");
        mockMvc.perform(put("/api/v1/monthly-budget").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(budgetBody("6000", "500", "400", "588", "800", "200")))
                .andExpect(status().isOk());

        JsonNode json = getBudget(asOtherUser());
        assertThat(decimal(json, "netIncome")).isEqualByComparingTo("0");
        assertThat(decimal(json, "fixedCostsPerMonth")).isEqualByComparingTo("0");
        assertThat(decimal(json, "available")).isEqualByComparingTo("0");
    }

    @Test
    void unauthenticated_requests_return_401() throws Exception {
        mockMvc.perform(get("/api/v1/monthly-budget"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/v1/monthly-budget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(budgetBody("6000", "0", "0", "0", "0", "0")))
                .andExpect(status().isUnauthorized());
    }
}
