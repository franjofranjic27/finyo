package ch.finyo.tax;

import ch.finyo.BaseIntegrationTest;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests for tax scenarios
 * (/api/v1/tax/years/{year}/scenarios).
 *
 * Runs against a real PostgreSQL Testcontainer with all Flyway migrations
 * applied, so V18 (tax_scenario incl. the partial unique index
 * ux_tax_scenario_default_per_year and the ON DELETE CASCADE FK to tax_year)
 * and the seeded SG rate tables (V10/V11/V17) back the assertions.
 *
 * Focus areas:
 *   1. Full lifecycle: create ×2 → list (newest first, embedded calculation) →
 *      atomic default switching (must survive the partial unique index) →
 *      idempotent re-default → deleting the default.
 *   2. A second default snapshot for the same year → 409 ProblemDetail.
 *   3. Lazy tax_year creation on the first scenario of a year.
 *   4. Multi-tenant isolation: foreign scenarios are invisible and immutable.
 *   5. DB-level ON DELETE CASCADE of scenarios when a year is deleted.
 *   6. Bean/path validation → 400.
 */
class TaxScenarioIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaxScenarioRepository taxScenarioRepository;

    @Autowired
    private TaxYearRepository taxYearRepository;

    @Autowired
    private TaxPaymentRepository taxPaymentRepository;

    @Autowired
    private TaxDeadlineRepository taxDeadlineRepository;

    @BeforeEach
    void cleanTaxTables() {
        // Children first: tax_scenario/payment/deadline reference tax_year
        taxScenarioRepository.deleteAll();
        taxPaymentRepository.deleteAll();
        taxDeadlineRepository.deleteAll();
        taxYearRepository.deleteAll();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Inputs complete enough for the embedded calculation to run (seeded SG data). */
    private String scenarioBody(String name, boolean isDefault) {
        return objectMapper.writeValueAsString(Map.of(
                "name", name,
                "isDefault", isDefault,
                "cantonCode", "SG",
                "bfsNumber", 3203,
                "civilStatus", "SINGLE",
                "numberOfChildren", 0,
                "churchAffiliation", "NONE",
                "grossEmploymentIncome", 100_000,
                "pillar3aContribution", 7000
        ));
    }

    /**
     * Name only — insufficient inputs, the calculation must stay null.
     * isDefault must always be sent: it is a primitive boolean in the request
     * record and Jackson 3 rejects missing primitives (400 "Bad Request").
     */
    private String minimalScenarioBody(String name) {
        return objectMapper.writeValueAsString(Map.of("name", name, "isDefault", false));
    }

    private UUID postScenario(int year, String body) throws Exception {
        String response = mockMvc.perform(post("/api/v1/tax/years/{year}/scenarios", year).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    // =========================================================================
    // SECTION 1 — FULL LIFECYCLE
    // =========================================================================

    @Test
    void full_lifecycle_create_list_switch_default_idempotency_and_delete() throws Exception {
        // --- 1. POST creates the first snapshot with a live calculation ------
        mockMvc.perform(post("/api/v1/tax/years/2025/scenarios").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scenarioBody("Base", false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.taxYear", is(2025)))
                .andExpect(jsonPath("$.name", is("Base")))
                .andExpect(jsonPath("$.isDefault", is(false)))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.calculation.grandTotal", notNullValue()))
                .andExpect(jsonPath("$.inputs.grossEmploymentIncome", notNullValue()));

        // --- 2. A second POST is a NEW row, never an update ------------------
        UUID max3aId = postScenario(2025, scenarioBody("Max 3a", false));

        // --- 3. GET lists both, newest first ----------------------------------
        String listJson = mockMvc.perform(get("/api/v1/tax/years/2025/scenarios").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)))
                .andExpect(jsonPath("$[0].name", is("Max 3a")))
                .andExpect(jsonPath("$[1].name", is("Base")))
                .andExpect(jsonPath("$[0].isDefault", is(false)))
                .andExpect(jsonPath("$[1].isDefault", is(false)))
                .andExpect(jsonPath("$[0].calculation.grandTotal", notNullValue()))
                .andReturn().getResponse().getContentAsString();
        UUID baseId = UUID.fromString(objectMapper.readTree(listJson).get(1).get("id").asText());

        // --- 4. PATCH default on "Base" ---------------------------------------
        mockMvc.perform(patch("/api/v1/tax/years/2025/scenarios/{id}/default", baseId).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(baseId.toString())))
                .andExpect(jsonPath("$.isDefault", is(true)));
        mockMvc.perform(get("/api/v1/tax/years/2025/scenarios").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isDefault", is(false)))
                .andExpect(jsonPath("$[1].isDefault", is(true)));

        // --- 5. PATCH default on "Max 3a" switches atomically ------------------
        // (clear-then-set must survive the partial unique index at DB level)
        mockMvc.perform(patch("/api/v1/tax/years/2025/scenarios/{id}/default", max3aId).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault", is(true)));
        mockMvc.perform(get("/api/v1/tax/years/2025/scenarios").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isDefault", is(true)))
                .andExpect(jsonPath("$[1].isDefault", is(false)));

        // --- 6. PATCH default on the current default is idempotent -------------
        mockMvc.perform(patch("/api/v1/tax/years/2025/scenarios/{id}/default", max3aId).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault", is(true)));

        // --- 7. Deleting the default is allowed → 204 ---------------------------
        mockMvc.perform(delete("/api/v1/tax/years/2025/scenarios/{id}", max3aId).with(asUser()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/tax/years/2025/scenarios").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].name", is("Base")))
                .andExpect(jsonPath("$[0].isDefault", is(false)));
    }

    // =========================================================================
    // SECTION 2 — DEFAULT UNIQUENESS CONFLICT
    // =========================================================================

    @Test
    void creating_a_second_default_scenario_for_the_same_year_returns_409() throws Exception {
        postScenario(2025, scenarioBody("First default", true));

        mockMvc.perform(post("/api/v1/tax/years/2025/scenarios").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scenarioBody("Second default", true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title", is("Conflict")))
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.detail", containsString("already has a default scenario")));

        // Non-default snapshots are still accepted alongside the existing default
        mockMvc.perform(post("/api/v1/tax/years/2025/scenarios").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scenarioBody("Non-default", false)))
                .andExpect(status().isCreated());
        assertThat(taxScenarioRepository.count())
                .as("the rejected default must not have been persisted")
                .isEqualTo(2);
    }

    // =========================================================================
    // SECTION 3 — LAZY YEAR CREATION
    // =========================================================================

    @Test
    void posting_a_scenario_lazily_creates_the_missing_tax_year() throws Exception {
        mockMvc.perform(get("/api/v1/tax/years/2026").with(asUser()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/tax/years/2026/scenarios").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalScenarioBody("Early draft")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taxYear", is(2026)))
                .andExpect(jsonPath("$.calculation", nullValue()));

        // The minimal year now exists for the caller, starting in OPEN
        mockMvc.perform(get("/api/v1/tax/years/2026").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year", is(2026)))
                .andExpect(jsonPath("$.status", is("OPEN")));
    }

    @Test
    void listing_scenarios_of_a_never_created_year_returns_empty_list_not_404() throws Exception {
        mockMvc.perform(get("/api/v1/tax/years/2024/scenarios").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
    }

    // =========================================================================
    // SECTION 4 — MULTI-TENANT ISOLATION
    // =========================================================================

    @Test
    void other_user_cannot_see_default_or_delete_a_foreign_scenario() throws Exception {
        UUID ownersScenarioId = postScenario(2025, scenarioBody("Owner's scenario", false));
        // The attacker owns their OWN 2025 year, so loadYear() succeeds for them —
        // the scenario lookup itself must still enforce ownership.
        taxYearRepository.save(TaxYear.builder()
                .userId(OTHER_USER_ID)
                .year(2025)
                .status(TaxYearStatus.OPEN)
                .build());

        mockMvc.perform(get("/api/v1/tax/years/2025/scenarios").with(asOtherUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));

        mockMvc.perform(patch("/api/v1/tax/years/2025/scenarios/{id}/default", ownersScenarioId)
                        .with(asOtherUser()))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/tax/years/2025/scenarios/{id}", ownersScenarioId)
                        .with(asOtherUser()))
                .andExpect(status().isNotFound());

        // The owner's scenario must be completely untouched
        TaxScenario reloaded = taxScenarioRepository.findById(ownersScenarioId).orElseThrow();
        assertThat(reloaded.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(reloaded.isDefault()).isFalse();
    }

    // =========================================================================
    // SECTION 5 — DELETE CASCADE
    // =========================================================================

    @Test
    void deleting_a_tax_year_cascades_to_its_scenarios() throws Exception {
        UUID scenarioId = postScenario(2025, scenarioBody("Doomed", true));
        assertThat(taxScenarioRepository.findById(scenarioId)).isPresent();

        mockMvc.perform(delete("/api/v1/tax/years/2025").with(asUser()))
                .andExpect(status().isNoContent());

        assertThat(taxScenarioRepository.findById(scenarioId))
                .as("ON DELETE CASCADE must remove the scenario together with the year")
                .isEmpty();
        // The year is gone, so the scenario list degrades to the empty state
        mockMvc.perform(get("/api/v1/tax/years/2025/scenarios").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
    }

    // =========================================================================
    // SECTION 6 — VALIDATION
    // =========================================================================

    @Test
    void scenario_with_blank_name_returns_400() throws Exception {
        mockMvc.perform(post("/api/v1/tax/years/2025/scenarios").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalScenarioBody("   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Validation Failed")));
    }

    @Test
    void year_below_supported_range_returns_400() throws Exception {
        mockMvc.perform(get("/api/v1/tax/years/2019/scenarios").with(asUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Validation Failed")));

        mockMvc.perform(post("/api/v1/tax/years/2019/scenarios").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(minimalScenarioBody("Too early")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Validation Failed")));
    }
}
