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
 * Integration tests for POST /api/v1/fixed-costs/bulk: idempotent re-import
 * (upsert by normalized name), payload validation (item constraints, empty
 * list, size cap), tenant isolation and authentication.
 */
class FixedCostBulkIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FixedCostRepository fixedCostRepository;

    @BeforeEach
    void cleanUp() {
        fixedCostRepository.deleteAll();
    }

    private Map<String, Object> item(String name, String interval, String amount) {
        return Map.of(
                "name", name,
                "category", "Living",
                "paymentInterval", interval,
                "amount", amount);
    }

    @SafeVarargs
    private String bulkBody(Map<String, Object>... items) {
        return objectMapper.writeValueAsString(Map.of("items", List.of(items)));
    }

    private BigDecimal decimal(JsonNode node, String field) {
        return new BigDecimal(node.get(field).asText());
    }

    @Test
    void reimporting_the_same_names_updates_instead_of_duplicating() throws Exception {
        mockMvc.perform(post("/api/v1/fixed-costs/bulk").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkBody(
                                item("Netflix", "MONTHLY", "20"),
                                item("Insurance", "YEARLY", "600"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(2)))
                .andExpect(jsonPath("$.updated", is(0)))
                .andExpect(jsonPath("$.failed", is(0)))
                .andExpect(jsonPath("$.errors.length()", is(0)));

        // second import matches by normalized name despite different casing/whitespace
        mockMvc.perform(post("/api/v1/fixed-costs/bulk").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkBody(
                                item("  NETFLIX ", "MONTHLY", "25"),
                                item("insurance", "YEARLY", "720"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(0)))
                .andExpect(jsonPath("$.updated", is(2)))
                .andExpect(jsonPath("$.failed", is(0)))
                .andExpect(jsonPath("$.errors.length()", is(0)));

        // row count is stable and the list totals reflect the upserted values:
        // 25 * 12 + 720 = 1020 per year, 25 + 60 = 85 per month
        String list = mockMvc.perform(get("/api/v1/fixed-costs").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", is(2)))
                .andReturn().getResponse().getContentAsString();
        JsonNode listJson = objectMapper.readTree(list);
        assertThat(decimal(listJson, "totalPerYear")).isEqualByComparingTo("1020.00");
        assertThat(decimal(listJson, "totalPerMonth")).isEqualByComparingTo("85.00");
        assertThat(listJson.get("items").findValues("name"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("  NETFLIX ", "insurance");
    }

    @Test
    void duplicate_names_within_one_batch_collapse_into_a_single_row_last_wins() throws Exception {
        mockMvc.perform(post("/api/v1/fixed-costs/bulk").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkBody(
                                item("Netflix", "MONTHLY", "20"),
                                item("netflix", "MONTHLY", "30"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(1)))
                .andExpect(jsonPath("$.updated", is(1)))
                .andExpect(jsonPath("$.failed", is(0)));

        String list = mockMvc.perform(get("/api/v1/fixed-costs").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", is(1)))
                .andExpect(jsonPath("$.items[0].name", is("netflix")))
                .andReturn().getResponse().getContentAsString();
        assertThat(decimal(objectMapper.readTree(list).get("items").get(0), "amount"))
                .isEqualByComparingTo("30");
    }

    @Test
    void one_invalid_item_rejects_the_whole_request_with_400() throws Exception {
        mockMvc.perform(post("/api/v1/fixed-costs/bulk").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkBody(
                                item("Netflix", "MONTHLY", "20"),
                                item("Broken", "MONTHLY", "-1"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/fixed-costs").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", is(0)));
    }

    @Test
    void empty_items_list_returns_400() throws Exception {
        mockMvc.perform(post("/api/v1/fixed-costs/bulk").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("items", List.of()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void more_than_500_items_returns_400() throws Exception {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            items.add(item("Cost " + i, "MONTHLY", "1"));
        }

        mockMvc.perform(post("/api/v1/fixed-costs/bulk").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("items", items))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/fixed-costs").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", is(0)));
    }

    @Test
    void bulk_imported_costs_are_not_visible_to_other_users() throws Exception {
        mockMvc.perform(post("/api/v1/fixed-costs/bulk").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkBody(item("Rent", "MONTHLY", "1500"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(1)));

        mockMvc.perform(get("/api/v1/fixed-costs").with(asOtherUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", is(0)));

        // the other user's import must not update the owner's row of the same name
        mockMvc.perform(post("/api/v1/fixed-costs/bulk").with(asOtherUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkBody(item("Rent", "MONTHLY", "1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(1)))
                .andExpect(jsonPath("$.updated", is(0)));

        String ownerList = mockMvc.perform(get("/api/v1/fixed-costs").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", is(1)))
                .andReturn().getResponse().getContentAsString();
        assertThat(decimal(objectMapper.readTree(ownerList).get("items").get(0), "amount"))
                .isEqualByComparingTo("1500");
    }

    @Test
    void unauthenticated_request_returns_401() throws Exception {
        mockMvc.perform(post("/api/v1/fixed-costs/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkBody(item("Netflix", "MONTHLY", "20"))))
                .andExpect(status().isUnauthorized());
    }
}
