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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for /api/v1/fixed-costs: CRUD lifecycle including the
 * YEARLY-to-monthly conversion in the list totals, validation, tenant
 * isolation and authentication.
 */
class FixedCostIT extends BaseIntegrationTest {

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

    private String fixedCostBody(String name, String interval, String amount) {
        return objectMapper.writeValueAsString(Map.of(
                "name", name,
                "category", "Living",
                "paymentInterval", interval,
                "amount", amount));
    }

    private BigDecimal decimal(JsonNode node, String field) {
        return new BigDecimal(node.get(field).asText());
    }

    @Test
    void full_lifecycle_create_list_update_delete_with_yearly_conversion() throws Exception {
        String netflix = mockMvc.perform(post("/api/v1/fixed-costs").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixedCostBody("Netflix", "MONTHLY", "20")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Netflix")))
                .andExpect(jsonPath("$.paymentInterval", is("MONTHLY")))
                .andReturn().getResponse().getContentAsString();
        JsonNode netflixJson = objectMapper.readTree(netflix);
        UUID netflixId = UUID.fromString(netflixJson.get("id").asText());
        assertThat(decimal(netflixJson, "amountPerYear")).isEqualByComparingTo("240.00");
        assertThat(decimal(netflixJson, "amountPerMonth")).isEqualByComparingTo("20.00");

        mockMvc.perform(post("/api/v1/fixed-costs").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixedCostBody("Insurance", "YEARLY", "600")))
                .andExpect(status().isCreated());

        // list is sorted by amountPerYear DESC (Insurance 600 > Netflix 240) and
        // totals convert the YEARLY cost to its monthly share (600 / 12 = 50)
        String list = mockMvc.perform(get("/api/v1/fixed-costs").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", is(2)))
                .andExpect(jsonPath("$.items[0].name", is("Insurance")))
                .andExpect(jsonPath("$.items[1].name", is("Netflix")))
                .andReturn().getResponse().getContentAsString();
        JsonNode listJson = objectMapper.readTree(list);
        assertThat(decimal(listJson, "totalPerYear")).isEqualByComparingTo("840.00");
        assertThat(decimal(listJson, "totalPerMonth")).isEqualByComparingTo("70.00");
        assertThat(decimal(listJson.get("items").get(0), "amountPerMonth")).isEqualByComparingTo("50.00");

        String updated = mockMvc.perform(put("/api/v1/fixed-costs/{id}", netflixId).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixedCostBody("Netflix", "MONTHLY", "25")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(decimal(objectMapper.readTree(updated), "amountPerYear")).isEqualByComparingTo("300.00");

        mockMvc.perform(delete("/api/v1/fixed-costs/{id}", netflixId).with(asUser()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/fixed-costs").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", is(1)));
    }

    @Test
    void create_with_blank_name_returns_400() throws Exception {
        mockMvc.perform(post("/api/v1/fixed-costs").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixedCostBody("   ", "MONTHLY", "20")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_with_negative_amount_returns_400() throws Exception {
        mockMvc.perform(post("/api/v1/fixed-costs").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixedCostBody("Netflix", "MONTHLY", "-1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void other_user_sees_an_empty_list_and_404_on_foreign_ids() throws Exception {
        String created = mockMvc.perform(post("/api/v1/fixed-costs").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixedCostBody("Rent", "MONTHLY", "1500")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        String otherList = mockMvc.perform(get("/api/v1/fixed-costs").with(asOtherUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", is(0)))
                .andReturn().getResponse().getContentAsString();
        assertThat(decimal(objectMapper.readTree(otherList), "totalPerYear")).isEqualByComparingTo("0");

        mockMvc.perform(put("/api/v1/fixed-costs/{id}", id).with(asOtherUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fixedCostBody("Hijacked", "MONTHLY", "1")))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/fixed-costs/{id}", id).with(asOtherUser()))
                .andExpect(status().isNotFound());

        // the foreign requests must not have altered the owner's data
        mockMvc.perform(get("/api/v1/fixed-costs").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()", is(1)))
                .andExpect(jsonPath("$.items[0].name", is("Rent")));
    }

    @Test
    void unauthenticated_request_returns_401() throws Exception {
        mockMvc.perform(get("/api/v1/fixed-costs"))
                .andExpect(status().isUnauthorized());
    }
}
