package ch.finyo.insurance;

import ch.finyo.BaseIntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for /api/v1/insurance against the V12-seeded catalogue.
 * Only user_insurance_status rows are cleaned between tests — the insurance
 * types themselves are reference data owned by the migration.
 */
class InsuranceIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserInsuranceStatusRepository userInsuranceStatusRepository;

    @BeforeEach
    void cleanUserStatuses() {
        userInsuranceStatusRepository.deleteAll();
    }

    @Test
    void overview_lists_the_seeded_catalogue_with_no_insurance_by_default() throws Exception {
        String response = mockMvc.perform(get("/api/v1/insurance").with(asUser()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode overview = objectMapper.readTree(response);
        assertThat(overview.size()).isPositive();
        assertThat(overview).allSatisfy(item ->
                assertThat(item.get("hasInsurance").asBoolean()).isFalse());
    }

    @Test
    void status_update_is_reflected_in_the_overview_and_scoped_to_the_user() throws Exception {
        String response = mockMvc.perform(get("/api/v1/insurance").with(asUser()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String typeId = objectMapper.readTree(response).get(0).get("id").asText();

        mockMvc.perform(patch("/api/v1/insurance/{id}/status", typeId).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("hasInsurance", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasInsurance", is(true)));

        mockMvc.perform(get("/api/v1/insurance").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hasInsurance", is(true)));

        // The other user's overview is unaffected
        mockMvc.perform(get("/api/v1/insurance").with(asOtherUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hasInsurance", is(false)));
    }

    @Test
    void status_update_for_an_unknown_insurance_type_returns_404() throws Exception {
        mockMvc.perform(patch("/api/v1/insurance/{id}/status", UUID.randomUUID()).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("hasInsurance", true))))
                .andExpect(status().isNotFound());
    }
}
