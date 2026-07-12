package ch.finyo.pillar3;

import ch.finyo.BaseIntegrationTest;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
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
 * End-to-end integration tests for pillar 3a scenarios
 * (/api/v1/pillar3/scenarios).
 *
 * Runs against a real PostgreSQL Testcontainer with all Flyway migrations
 * applied, so V26 (pillar3_scenario incl. the partial unique index
 * ux_pillar3_scenario_default_per_user and the ON DELETE SET NULL FK to
 * pillar3_product) and the seeded SG rate tables (V10/V11/V17) back the
 * assertions.
 *
 * Focus areas:
 *   1. Full lifecycle: create ×2 → list (newest first, embedded calculation) →
 *      atomic default switching (must survive the partial unique index) →
 *      idempotent re-default → delete.
 *   2. A second default snapshot for the same user → 409 ProblemDetail.
 *   3. Multi-tenant isolation: foreign scenarios are invisible and immutable.
 *   4. Race surface: a default inserted behind the API's back still blocks a
 *      second default.
 *   5. Product link: the linked product overrides the stored snapshot percent;
 *      deleting the product (ON DELETE SET NULL) degrades to the snapshot.
 *   6. Bean validation → 400; missing authentication → 401.
 */
class Pillar3ScenarioIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Pillar3ScenarioRepository scenarioRepository;

    @Autowired
    private Pillar3ProductRepository productRepository;

    @BeforeEach
    void cleanPillar3Tables() {
        // Scenarios first: they hold a (SET NULL) FK onto pillar3_product
        scenarioRepository.deleteAll();
        productRepository.deleteAll();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Complete request body. The primitives (assumedAnnualReturnPercent,
     * yearsToRetirement) must always be sent: Jackson 3 rejects missing
     * primitives (400 "Bad Request").
     */
    private String scenarioBody(String name, boolean isDefault, double returnPercent, int years, UUID productId) {
        ObjectNode body = objectMapper.createObjectNode()
                .put("name", name)
                .put("isDefault", isDefault)
                .put("currentBalance", 10_000)
                .put("annualContribution", 7_000)
                .put("assumedAnnualReturnPercent", returnPercent)
                .put("yearsToRetirement", years)
                .put("grossEmploymentIncome", 100_000)
                .put("civilStatus", "SINGLE")
                .put("cantonCode", "SG")
                .put("taxYear", 2025);
        if (productId != null) {
            body.put("productId", productId.toString());
        }
        return objectMapper.writeValueAsString(body);
    }

    private String scenarioBody(String name, boolean isDefault) {
        return scenarioBody(name, isDefault, 3.5, 10, null);
    }

    private UUID postScenario(String body) throws Exception {
        String response = mockMvc.perform(post("/api/v1/pillar3/scenarios").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private Pillar3Product saveProduct(String equityPct, String terPct) {
        return productRepository.save(Pillar3Product.builder()
                .provider("Testbank")
                .name("Test Fund 45")
                .isin("CH0000000001")
                .equityPct(new BigDecimal(equityPct))
                .terPct(new BigDecimal(terPct))
                .active(true)
                .sortOrder(0)
                .build());
    }

    // =========================================================================
    // SECTION 1 — FULL LIFECYCLE
    // =========================================================================

    @Test
    void full_lifecycle_create_list_switch_default_idempotency_and_delete() throws Exception {
        // --- 1. POST creates the first snapshot with a live calculation ------
        mockMvc.perform(post("/api/v1/pillar3/scenarios").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scenarioBody("Base", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Base")))
                .andExpect(jsonPath("$.isDefault", is(true)))
                .andExpect(jsonPath("$.product", nullValue()))
                .andExpect(jsonPath("$.effectiveReturnPercent", is(3.5)))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.calculation.projectedBalanceAtRetirement", notNullValue()))
                .andExpect(jsonPath("$.inputs.annualContribution", notNullValue()));

        // --- 2. A second POST is a NEW row, never an update ------------------
        UUID aggressiveId = postScenario(scenarioBody("Aggressive", false, 6.0, 25, null));

        // --- 3. GET lists both, newest first, each with a full projection -----
        String listJson = mockMvc.perform(get("/api/v1/pillar3/scenarios").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)))
                .andExpect(jsonPath("$[0].name", is("Aggressive")))
                .andExpect(jsonPath("$[1].name", is("Base")))
                .andExpect(jsonPath("$[0].isDefault", is(false)))
                .andExpect(jsonPath("$[1].isDefault", is(true)))
                .andExpect(jsonPath("$[0].calculation.projectedBalanceAtRetirement", notNullValue()))
                .andExpect(jsonPath("$[1].calculation.projectedBalanceAtRetirement", notNullValue()))
                .andExpect(jsonPath("$[0].calculation.yearlyProjection.length()", is(25)))
                .andExpect(jsonPath("$[1].calculation.yearlyProjection.length()", is(10)))
                .andReturn().getResponse().getContentAsString();
        UUID baseId = UUID.fromString(objectMapper.readTree(listJson).get(1).get("id").asText());

        // --- 4. PATCH default switches atomically ------------------------------
        // (clear-then-set must survive the partial unique index at DB level)
        mockMvc.perform(patch("/api/v1/pillar3/scenarios/{id}/default", aggressiveId).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(aggressiveId.toString())))
                .andExpect(jsonPath("$.isDefault", is(true)));
        mockMvc.perform(get("/api/v1/pillar3/scenarios").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isDefault", is(true)))
                .andExpect(jsonPath("$[1].isDefault", is(false)));

        // --- 5. PATCH default on the current default is idempotent -------------
        mockMvc.perform(patch("/api/v1/pillar3/scenarios/{id}/default", aggressiveId).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault", is(true)));

        // --- 6. Deleting the default is allowed → 204 ---------------------------
        mockMvc.perform(delete("/api/v1/pillar3/scenarios/{id}", aggressiveId).with(asUser()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/pillar3/scenarios").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].id", is(baseId.toString())))
                .andExpect(jsonPath("$[0].isDefault", is(false)));
    }

    // =========================================================================
    // SECTION 2 — DEFAULT UNIQUENESS CONFLICT
    // =========================================================================

    @Test
    void creating_a_second_default_scenario_returns_409() throws Exception {
        postScenario(scenarioBody("First default", true));

        mockMvc.perform(post("/api/v1/pillar3/scenarios").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scenarioBody("Second default", true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title", is("Conflict")))
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.detail", containsString("already has a default")));

        // Non-default snapshots are still accepted alongside the existing default
        mockMvc.perform(post("/api/v1/pillar3/scenarios").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scenarioBody("Non-default", false)))
                .andExpect(status().isCreated());
        assertThat(scenarioRepository.count())
                .as("the rejected default must not have been persisted")
                .isEqualTo(2);
    }

    // =========================================================================
    // SECTION 3 — MULTI-TENANT ISOLATION
    // =========================================================================

    @Test
    void other_user_cannot_see_default_or_delete_a_foreign_scenario() throws Exception {
        UUID ownersScenarioId = postScenario(scenarioBody("Owner's scenario", false));

        mockMvc.perform(get("/api/v1/pillar3/scenarios").with(asOtherUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));

        mockMvc.perform(patch("/api/v1/pillar3/scenarios/{id}/default", ownersScenarioId)
                        .with(asOtherUser()))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/pillar3/scenarios/{id}", ownersScenarioId)
                        .with(asOtherUser()))
                .andExpect(status().isNotFound());

        // The owner's scenario must be completely untouched
        Pillar3Scenario reloaded = scenarioRepository.findById(ownersScenarioId).orElseThrow();
        assertThat(reloaded.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(reloaded.isDefault()).isFalse();
    }

    // =========================================================================
    // SECTION 4 — RACE SURFACE
    // =========================================================================

    @Test
    void default_inserted_behind_the_api_still_blocks_a_second_default() throws Exception {
        // Simulates a competing writer that bypassed the endpoint's pre-check
        scenarioRepository.save(Pillar3Scenario.builder()
                .userId(TEST_USER_ID)
                .name("Direct default")
                .isDefault(true)
                .currentBalance(new BigDecimal("1000"))
                .annualContribution(new BigDecimal("7000"))
                .assumedAnnualReturnPercent(new BigDecimal("3.00"))
                .yearsToRetirement(5)
                .build());

        mockMvc.perform(post("/api/v1/pillar3/scenarios").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scenarioBody("API default", true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title", is("Conflict")))
                .andExpect(jsonPath("$.detail", containsString("already has a default")));
        assertThat(scenarioRepository.count()).isEqualTo(1);
    }

    // =========================================================================
    // SECTION 5 — PRODUCT LINK
    // =========================================================================

    @Test
    void linked_product_overrides_the_snapshot_and_its_deletion_degrades_to_the_snapshot() throws Exception {
        Pillar3Product product = saveProduct("45", "0.40");
        // 45% equity → 3.25% gross, minus 0.40 TER → 2.85% net
        double expectedNetRate = Pillar3ReturnModel
                .netReturnPct(product.getEquityPct(), product.getTerPct()).doubleValue();

        // The snapshot percent (5.0) deliberately differs from the product rate
        mockMvc.perform(post("/api/v1/pillar3/scenarios").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scenarioBody("Linked", false, 5.0, 10, product.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.product.id", is(product.getId().toString())))
                .andExpect(jsonPath("$.product.netReturnPct", is(2.85)))
                .andExpect(jsonPath("$.effectiveReturnPercent", is(expectedNetRate)))
                .andExpect(jsonPath("$.inputs.assumedAnnualReturnPercent", is(5.0)));

        // Deleting the product must NOT delete the scenario: the FK is
        // ON DELETE SET NULL, so the stored snapshot percent takes over.
        productRepository.deleteById(product.getId());

        mockMvc.perform(get("/api/v1/pillar3/scenarios").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].name", is("Linked")))
                .andExpect(jsonPath("$[0].product", nullValue()))
                .andExpect(jsonPath("$[0].effectiveReturnPercent", is(5.0)))
                .andExpect(jsonPath("$[0].calculation.projectedBalanceAtRetirement", notNullValue()));
    }

    @Test
    void creating_a_scenario_with_an_unknown_productId_returns_404() throws Exception {
        mockMvc.perform(post("/api/v1/pillar3/scenarios").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scenarioBody("Ghost product", false, 3.5, 10, UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title", is("Resource Not Found")));
        assertThat(scenarioRepository.count()).isZero();
    }

    // =========================================================================
    // SECTION 6 — VALIDATION & AUTHENTICATION
    // =========================================================================

    @Test
    void scenario_with_blank_name_returns_400() throws Exception {
        mockMvc.perform(post("/api/v1/pillar3/scenarios").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scenarioBody("   ", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Validation Failed")));
    }

    @Test
    void return_percent_above_20_returns_400() throws Exception {
        mockMvc.perform(post("/api/v1/pillar3/scenarios").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scenarioBody("Too optimistic", false, 25.0, 10, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Validation Failed")));
    }

    @Test
    void zero_years_to_retirement_returns_400() throws Exception {
        mockMvc.perform(post("/api/v1/pillar3/scenarios").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scenarioBody("Retired already", false, 3.5, 0, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Validation Failed")));
    }

    @Test
    void unauthenticated_request_returns_401() throws Exception {
        mockMvc.perform(get("/api/v1/pillar3/scenarios"))
                .andExpect(status().isUnauthorized());
    }
}
