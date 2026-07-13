package ch.finyo.salary;

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
 * Integration tests for /api/v1/salary: default view with Swiss rates (never 404,
 * never persisted on GET), PUT upsert semantics, the computed gross-to-net result,
 * validation, tenant isolation and authentication.
 */
class SalaryIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SalaryProfileRepository salaryProfileRepository;

    @BeforeEach
    void cleanUp() {
        salaryProfileRepository.deleteAll();
    }

    private String salaryBody(String grossMonthly, boolean thirteenthSalary,
                              String ahvPct, String alvPct, String nbuPct, String ktgPct,
                              String pensionFixed, String otherFixed) {
        return objectMapper.writeValueAsString(Map.of(
                "grossMonthly", grossMonthly,
                "thirteenthSalary", thirteenthSalary,
                "ahvPct", ahvPct,
                "alvPct", alvPct,
                "nbuPct", nbuPct,
                "ktgPct", ktgPct,
                "pensionFixed", pensionFixed,
                "otherFixed", otherFixed));
    }

    private String referenceBody() {
        return salaryBody("5787", false, "5.3", "1.1", "0.455", "0.17", "85.35", "11");
    }

    private String yearlyBody(String grossYearly, boolean thirteenthSalary) {
        return objectMapper.writeValueAsString(Map.of(
                "inputMode", "YEARLY",
                "grossYearly", grossYearly,
                "thirteenthSalary", thirteenthSalary,
                "ahvPct", "5.3",
                "alvPct", "1.1",
                "nbuPct", "0.455",
                "ktgPct", "0.17",
                "pensionFixed", "85.35",
                "otherFixed", "11"));
    }

    private JsonNode getSalary(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor user) throws Exception {
        String body = mockMvc.perform(get("/api/v1/salary").with(user))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private BigDecimal decimal(JsonNode node, String field) {
        return new BigDecimal(node.get(field).asText());
    }

    @Test
    void get_returns_the_swiss_defaults_without_persisting_when_no_profile_exists() throws Exception {
        JsonNode json = getSalary(asUser());

        assertThat(decimal(json, "grossMonthly")).isEqualByComparingTo("0");
        assertThat(json.get("thirteenthSalary").asBoolean()).isFalse();
        assertThat(decimal(json, "ahvPct")).isEqualByComparingTo("5.3");
        assertThat(decimal(json, "alvPct")).isEqualByComparingTo("1.1");
        assertThat(decimal(json, "nbuPct")).isEqualByComparingTo("0.455");
        assertThat(decimal(json, "ktgPct")).isEqualByComparingTo("0.17");
        assertThat(decimal(json, "pensionFixed")).isEqualByComparingTo("0");
        assertThat(decimal(json, "otherFixed")).isEqualByComparingTo("0");

        JsonNode result = json.get("result");
        assertThat(result.get("deductions")).hasSize(6);
        assertThat(decimal(result, "netMonthly")).isEqualByComparingTo("0");
        assertThat(decimal(result, "totalDeductionsPct")).isEqualByComparingTo("0");

        assertThat(salaryProfileRepository.count()).isZero();
    }

    @Test
    void put_upserts_the_profile_and_get_reflects_stored_values_and_computed_result() throws Exception {
        String putResponse = mockMvc.perform(put("/api/v1/salary").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(referenceBody()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode putJson = objectMapper.readTree(putResponse);
        assertThat(decimal(putJson.get("result"), "netMonthly")).isEqualByComparingTo("5284.10");

        JsonNode json = getSalary(asUser());
        assertThat(decimal(json, "grossMonthly")).isEqualByComparingTo("5787");
        assertThat(json.get("thirteenthSalary").asBoolean()).isFalse();
        assertThat(decimal(json, "pensionFixed")).isEqualByComparingTo("85.35");

        JsonNode result = json.get("result");
        assertThat(decimal(result, "grossYearly")).isEqualByComparingTo("69444.00");
        assertThat(decimal(result, "totalPerMonth")).isEqualByComparingTo("502.90");
        assertThat(decimal(result, "netMonthly")).isEqualByComparingTo("5284.10");
        assertThat(decimal(result, "totalDeductionsPct")).isEqualByComparingTo("8.7");

        JsonNode deductions = result.get("deductions");
        assertThat(deductions).hasSize(6);
        assertThat(deductions.get(0).get("type").asText()).isEqualTo("AHV");
        assertThat(decimal(deductions.get(0), "pct")).isEqualByComparingTo("5.3");
        assertThat(decimal(deductions.get(0), "perMonth")).isEqualByComparingTo("306.70");
        assertThat(deductions.get(4).get("type").asText()).isEqualTo("PENSION");
        assertThat(deductions.get(4).get("pct").isNull()).isTrue();
        assertThat(decimal(deductions.get(4), "perMonth")).isEqualByComparingTo("85.35");
        assertThat(deductions.get(5).get("type").asText()).isEqualTo("OTHER");

        // second PUT updates the same row instead of creating another one
        mockMvc.perform(put("/api/v1/salary").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(salaryBody("6000", true, "5.3", "1.1", "0.455", "0.17", "85.35", "11")))
                .andExpect(status().isOk());
        assertThat(salaryProfileRepository.count()).isEqualTo(1);

        JsonNode updated = getSalary(asUser());
        assertThat(decimal(updated, "grossMonthly")).isEqualByComparingTo("6000");
        assertThat(updated.get("thirteenthSalary").asBoolean()).isTrue();
        assertThat(decimal(updated.get("result"), "grossYearly")).isEqualByComparingTo("78000.00");
    }

    @Test
    void put_in_yearly_mode_stores_the_yearly_gross_and_derives_the_monthly_view() throws Exception {
        mockMvc.perform(put("/api/v1/salary").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(yearlyBody("100000", true)))
                .andExpect(status().isOk());

        JsonNode json = getSalary(asUser());
        assertThat(json.get("inputMode").asText()).isEqualTo("YEARLY");
        assertThat(decimal(json, "grossYearly")).isEqualByComparingTo("100000");
        // 100000 / 13 = 7692.307... -> 7692.31 (HALF_UP)
        assertThat(decimal(json, "grossMonthly")).isEqualByComparingTo("7692.31");

        JsonNode result = json.get("result");
        // the entered yearly gross is redisplayed exactly as the annual total
        assertThat(decimal(result, "grossYearly")).isEqualByComparingTo("100000.00");
        assertThat(decimal(result, "grossMonthly")).isEqualByComparingTo("7692.31");
    }

    @Test
    void put_in_monthly_mode_echoes_the_derived_yearly_gross() throws Exception {
        mockMvc.perform(put("/api/v1/salary").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(referenceBody()))
                .andExpect(status().isOk());

        JsonNode json = getSalary(asUser());
        assertThat(json.get("inputMode").asText()).isEqualTo("MONTHLY");
        assertThat(decimal(json, "grossMonthly")).isEqualByComparingTo("5787");
        assertThat(decimal(json, "grossYearly")).isEqualByComparingTo("69444");
    }

    @Test
    void put_in_yearly_mode_without_gross_yearly_returns_400() throws Exception {
        mockMvc.perform(put("/api/v1/salary").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "inputMode", "YEARLY",
                                "grossMonthly", "5787",
                                "thirteenthSalary", false,
                                "ahvPct", "5.3",
                                "alvPct", "1.1",
                                "nbuPct", "0.455",
                                "ktgPct", "0.17",
                                "pensionFixed", "85.35",
                                "otherFixed", "11"))))
                .andExpect(status().isBadRequest());

        assertThat(salaryProfileRepository.count()).isZero();
    }

    @Test
    void put_with_negative_gross_or_out_of_range_percentage_returns_400() throws Exception {
        mockMvc.perform(put("/api/v1/salary").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(salaryBody("-1", false, "5.3", "1.1", "0.455", "0.17", "0", "0")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/salary").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(salaryBody("5787", false, "101", "1.1", "0.455", "0.17", "0", "0")))
                .andExpect(status().isBadRequest());

        assertThat(salaryProfileRepository.count()).isZero();
    }

    @Test
    void put_with_a_gross_monthly_above_100_million_returns_400() throws Exception {
        mockMvc.perform(put("/api/v1/salary").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(salaryBody("100000001", false, "5.3", "1.1", "0.455", "0.17", "0", "0")))
                .andExpect(status().isBadRequest());

        assertThat(salaryProfileRepository.count()).isZero();
    }

    @Test
    void put_with_a_gross_yearly_above_100_million_returns_400() throws Exception {
        mockMvc.perform(put("/api/v1/salary").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(yearlyBody("100000000.01", false)))
                .andExpect(status().isBadRequest());

        assertThat(salaryProfileRepository.count()).isZero();
    }

    @Test
    void other_user_still_sees_the_default_view_not_the_owners_profile() throws Exception {
        mockMvc.perform(put("/api/v1/salary").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(referenceBody()))
                .andExpect(status().isOk());

        JsonNode json = getSalary(asOtherUser());
        assertThat(decimal(json, "grossMonthly")).isEqualByComparingTo("0");
        assertThat(decimal(json, "ahvPct")).isEqualByComparingTo("5.3");
        assertThat(decimal(json.get("result"), "netMonthly")).isEqualByComparingTo("0");
        assertThat(salaryProfileRepository.count()).isEqualTo(1);
    }

    @Test
    void unauthenticated_requests_return_401() throws Exception {
        mockMvc.perform(get("/api/v1/salary"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/v1/salary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(referenceBody()))
                .andExpect(status().isUnauthorized());
    }
}
