package ch.finyo.tax;

import ch.finyo.BaseIntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests for the tax-year domain
 * (/api/v1/tax/years and the backward-compatible /api/v1/tax/calculate).
 *
 * Runs against a real PostgreSQL Testcontainer with all Flyway migrations
 * applied, so the seeded SG rate tables (V10/V11) and the official commune
 * multipliers (V17, e.g. St. Gallen BFS 3203 in 2025: roman-catholic 0.26,
 * protestant NULL — the city spans several protestant Kirchgemeinden with
 * diverging Steuerfüsse) are available to the calculation.
 *
 * Focus areas:
 *   1. Full lifecycle: upsert → list → detail → status transitions →
 *      payments → derived figures (expectedTax/paidTotal/openAmount).
 *   2. Status state machine conflicts surface as 409 ProblemDetail.
 *   3. Multi-tenant isolation: foreign years and payments return 404.
 *   4. DB-level ON DELETE CASCADE of payments when a year is deleted.
 *   5. Bean validation → 400.
 *   6. Backward compatibility of /tax/calculate with and without
 *      churchAffiliation.
 */
class TaxYearIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaxYearRepository taxYearRepository;

    @Autowired
    private TaxPaymentRepository taxPaymentRepository;

    @Autowired
    private TaxDeadlineRepository taxDeadlineRepository;

    @BeforeEach
    void cleanTaxTables() {
        taxPaymentRepository.deleteAll();
        taxDeadlineRepository.deleteAll();
        taxYearRepository.deleteAll();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String yearRequestBody(int grossEmploymentIncome) {
        return objectMapper.writeValueAsString(Map.of(
                "cantonCode", "SG",
                "bfsNumber", 3203,
                "civilStatus", "SINGLE",
                "numberOfChildren", 0,
                "churchAffiliation", "NONE",
                "grossEmploymentIncome", grossEmploymentIncome,
                "pillar3aContribution", 7000
        ));
    }

    private String statusBody(String status) {
        return objectMapper.writeValueAsString(Map.of("status", status));
    }

    private BigDecimal decimal(JsonNode node, String field) {
        return new BigDecimal(node.get(field).asText());
    }

    // =========================================================================
    // SECTION 1 — FULL LIFECYCLE
    // =========================================================================

    @Test
    void full_lifecycle_upsert_list_status_transitions_payments_and_conflicts() throws Exception {
        // --- 1. PUT creates the year with a live calculation -----------------
        String created = mockMvc.perform(put("/api/v1/tax/years/2025").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(yearRequestBody(100_000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year", is(2025)))
                .andExpect(jsonPath("$.status", is("OPEN")))
                .andExpect(jsonPath("$.calculation", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        JsonNode detail = objectMapper.readTree(created);
        BigDecimal expectedTax = decimal(detail, "expectedTax");
        assertThat(expectedTax)
                .as("expectedTax must equal the calculation's grandTotal while OPEN")
                .isEqualByComparingTo(new BigDecimal(detail.get("calculation").get("grandTotal").asText()))
                .isGreaterThan(BigDecimal.ZERO);

        // --- 2. GET /years lists it with derived figures ---------------------
        String listJson = mockMvc.perform(get("/api/v1/tax/years").with(asUser()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode list = objectMapper.readTree(listJson);
        assertThat(list.size()).isEqualTo(1);
        JsonNode summary = list.get(0);
        assertThat(summary.get("year").asInt()).isEqualTo(2025);
        assertThat(decimal(summary, "expectedTax")).isEqualByComparingTo(expectedTax);
        assertThat(decimal(summary, "paidTotal")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(decimal(summary, "openAmount")).isEqualByComparingTo(expectedTax);

        // --- 3. GET /years/2025 carries the full calculation -----------------
        mockMvc.perform(get("/api/v1/tax/years/2025").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculation.grossIncome", notNullValue()))
                .andExpect(jsonPath("$.calculation.taxableIncome", notNullValue()));

        // --- 4. OPEN → FILED stamps filedAt ----------------------------------
        mockMvc.perform(patch("/api/v1/tax/years/2025/status").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("FILED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FILED")))
                .andExpect(jsonPath("$.filedAt", notNullValue()));

        // --- 5. FILED → FILED is a same-status conflict → 409 ProblemDetail --
        mockMvc.perform(patch("/api/v1/tax/years/2025/status").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("FILED")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title", is("Conflict")))
                .andExpect(jsonPath("$.status", is(409)));

        // --- 6. POST payment → 201 -------------------------------------------
        String paymentJson = mockMvc.perform(post("/api/v1/tax/years/2025/payments").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "paymentDate", "2025-06-30",
                                "amount", 5000,
                                "label", "Provisional invoice"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andReturn().getResponse().getContentAsString();
        UUID paymentId = UUID.fromString(objectMapper.readTree(paymentJson).get("id").asText());

        // --- 7. Derived figures update: paidTotal and openAmount -------------
        String afterPayment = mockMvc.perform(get("/api/v1/tax/years/2025").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments.length()", is(1)))
                .andReturn().getResponse().getContentAsString();
        JsonNode afterPaymentNode = objectMapper.readTree(afterPayment);
        assertThat(decimal(afterPaymentNode, "paidTotal")).isEqualByComparingTo("5000");
        assertThat(decimal(afterPaymentNode, "openAmount"))
                .isEqualByComparingTo(expectedTax.subtract(new BigDecimal("5000")));

        // --- 8. DELETE payment → 204, figures roll back -----------------------
        mockMvc.perform(delete("/api/v1/tax/years/2025/payments/{id}", paymentId).with(asUser()))
                .andExpect(status().isNoContent());
        String afterDelete = mockMvc.perform(get("/api/v1/tax/years/2025").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments.length()", is(0)))
                .andReturn().getResponse().getContentAsString();
        assertThat(decimal(objectMapper.readTree(afterDelete), "paidTotal"))
                .isEqualByComparingTo(BigDecimal.ZERO);

        // --- 9. FILED → PAID (forward skip) stamps assessedAt ------------------
        mockMvc.perform(patch("/api/v1/tax/years/2025/status").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("PAID")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PAID")))
                .andExpect(jsonPath("$.assessedAt", notNullValue()));

        // --- 10. PAID → OPEN is more than one step back → 409 ------------------
        mockMvc.perform(patch("/api/v1/tax/years/2025/status").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("OPEN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title", is("Conflict")));
    }

    // =========================================================================
    // SECTION 2 — MULTI-TENANT ISOLATION
    // =========================================================================

    @Test
    void other_user_cannot_read_patch_or_delete_a_foreign_tax_year() throws Exception {
        TaxYear owned = taxYearRepository.save(TaxYear.builder()
                .userId(TEST_USER_ID)
                .year(2025)
                .status(TaxYearStatus.OPEN)
                .build());

        mockMvc.perform(get("/api/v1/tax/years/2025").with(asOtherUser()))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/tax/years/2025/status").with(asOtherUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusBody("FILED")))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/tax/years/2025").with(asOtherUser()))
                .andExpect(status().isNotFound());

        // The owner's year must be completely untouched
        TaxYear reloaded = taxYearRepository.findById(owned.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TaxYearStatus.OPEN);
    }

    /**
     * PUT /{year} is an upsert scoped per user: another user PUTting the same
     * calendar year creates their OWN row and must never overwrite the first
     * user's inputs. (tax_year has a UNIQUE(user_id, tax_year) constraint.)
     */
    @Test
    void put_by_other_user_creates_a_separate_year_and_does_not_touch_the_owners_data() throws Exception {
        mockMvc.perform(put("/api/v1/tax/years/2025").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(yearRequestBody(100_000)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/tax/years/2025").with(asOtherUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(yearRequestBody(50_000)))
                .andExpect(status().isOk());

        String ownerDetail = mockMvc.perform(get("/api/v1/tax/years/2025").with(asUser()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode inputs = objectMapper.readTree(ownerDetail).get("inputs");
        assertThat(new BigDecimal(inputs.get("grossEmploymentIncome").asText()))
                .as("owner's income must not be overwritten by the other user's upsert")
                .isEqualByComparingTo("100000");
        assertThat(taxYearRepository.count()).isEqualTo(2);
    }

    @Test
    void other_user_cannot_delete_a_foreign_payment_even_when_owning_a_year_with_the_same_number() throws Exception {
        TaxYear ownersYear = taxYearRepository.save(TaxYear.builder()
                .userId(TEST_USER_ID)
                .year(2025)
                .status(TaxYearStatus.OPEN)
                .build());
        TaxPayment ownersPayment = taxPaymentRepository.save(TaxPayment.builder()
                .taxYearId(ownersYear.getId())
                .userId(TEST_USER_ID)
                .paymentDate(LocalDate.of(2025, Month.JUNE, 30))
                .amount(new BigDecimal("5000"))
                .build());
        // The attacker owns their OWN 2025 year, so loadYear() succeeds for them —
        // the payment lookup itself must still enforce ownership.
        taxYearRepository.save(TaxYear.builder()
                .userId(OTHER_USER_ID)
                .year(2025)
                .status(TaxYearStatus.OPEN)
                .build());

        mockMvc.perform(delete("/api/v1/tax/years/2025/payments/{id}", ownersPayment.getId())
                        .with(asOtherUser()))
                .andExpect(status().isNotFound());

        assertThat(taxPaymentRepository.findById(ownersPayment.getId()))
                .as("foreign payment must not be deleted")
                .isPresent();
    }

    // =========================================================================
    // SECTION 3 — DELETE CASCADE
    // =========================================================================

    @Test
    void deleting_a_tax_year_cascades_to_its_payments() throws Exception {
        mockMvc.perform(put("/api/v1/tax/years/2025").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(yearRequestBody(100_000)))
                .andExpect(status().isOk());
        String paymentJson = mockMvc.perform(post("/api/v1/tax/years/2025/payments").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "paymentDate", "2025-06-30",
                                "amount", 2500))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID paymentId = UUID.fromString(objectMapper.readTree(paymentJson).get("id").asText());

        mockMvc.perform(delete("/api/v1/tax/years/2025").with(asUser()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/tax/years/2025").with(asUser()))
                .andExpect(status().isNotFound());
        assertThat(taxPaymentRepository.findById(paymentId))
                .as("ON DELETE CASCADE must remove the payment together with the year")
                .isEmpty();

        // Re-creating the same year must start with a clean payment list
        mockMvc.perform(put("/api/v1/tax/years/2025").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(yearRequestBody(100_000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payments.length()", is(0)));
    }

    // =========================================================================
    // SECTION 4 — NOT FOUND AND VALIDATION
    // =========================================================================

    @Test
    void getting_a_never_created_year_returns_404() throws Exception {
        mockMvc.perform(get("/api/v1/tax/years/2024").with(asUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    void upsert_with_year_outside_supported_range_returns_400() throws Exception {
        mockMvc.perform(put("/api/v1/tax/years/1999").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(yearRequestBody(100_000)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Validation Failed")));
    }

    @Test
    void upsert_with_negative_gross_income_returns_400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "cantonCode", "SG",
                "civilStatus", "SINGLE",
                "grossEmploymentIncome", -1
        ));

        mockMvc.perform(put("/api/v1/tax/years/2025").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Validation Failed")));
    }

    @Test
    void payment_without_payment_date_returns_400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("amount", 100));

        mockMvc.perform(post("/api/v1/tax/years/2025/payments").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Validation Failed")));
    }

    @Test
    void payment_with_negative_amount_returns_400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "paymentDate", "2025-06-30",
                "amount", -100
        ));

        mockMvc.perform(post("/api/v1/tax/years/2025/payments").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // SECTION 5 — DEADLINES
    // =========================================================================

    @Test
    void deadline_done_flag_can_be_toggled_via_put() throws Exception {
        mockMvc.perform(put("/api/v1/tax/years/2025").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(yearRequestBody(100_000)))
                .andExpect(status().isOk());

        String deadlineJson = mockMvc.perform(post("/api/v1/tax/years/2025/deadlines").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dueDate", "2026-03-31",
                                "label", "File tax return",
                                "done", false))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.done", is(false)))
                .andReturn().getResponse().getContentAsString();
        UUID deadlineId = UUID.fromString(objectMapper.readTree(deadlineJson).get("id").asText());

        mockMvc.perform(put("/api/v1/tax/years/2025/deadlines/{id}", deadlineId).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dueDate", "2026-03-31",
                                "label", "File tax return",
                                "done", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done", is(true)));

        mockMvc.perform(get("/api/v1/tax/years/2025").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deadlines.length()", is(1)))
                .andExpect(jsonPath("$.deadlines[0].done", is(true)));
    }

    // =========================================================================
    // SECTION 6 — BACKWARD COMPATIBILITY OF /tax/calculate
    // =========================================================================

    /**
     * Pre-church-tax clients do not send churchAffiliation at all — the
     * calculation must still succeed and yield zero church tax.
     */
    @Test
    void calculate_without_church_affiliation_returns_200_and_zero_church_tax() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "taxYear", 2025,
                "cantonCode", "SG",
                "bfsNumber", 3203,
                "civilStatus", "SINGLE",
                "numberOfChildren", 0,
                "grossEmploymentIncome", 100000
        ));

        String response = mockMvc.perform(post("/api/v1/tax/calculate").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode result = objectMapper.readTree(response);
        assertThat(decimal(result, "churchTax")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(decimal(result, "communalTax"))
                .as("seeded St. Gallen multiplier (V17) must still produce communal tax")
                .isGreaterThan(BigDecimal.ZERO);
    }

    /**
     * St. Gallen 2025 has NO uniform protestant Steuerfuss (three Kirchgemeinden
     * with diverging values → column is NULL in V17). A protestant affiliation
     * must then simply yield zero church tax instead of failing.
     */
    @Test
    void calculate_with_protestant_affiliation_and_null_multiplier_returns_zero_church_tax() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "taxYear", 2025,
                "cantonCode", "SG",
                "bfsNumber", 3203,
                "civilStatus", "SINGLE",
                "numberOfChildren", 0,
                "churchAffiliation", "PROTESTANT",
                "grossEmploymentIncome", 100000
        ));

        String response = mockMvc.perform(post("/api/v1/tax/calculate").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(decimal(objectMapper.readTree(response), "churchTax"))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    /**
     * The commune picker must never be empty for years beyond the seeded data:
     * /communes falls back to the latest seeded year (2026, without Wil which
     * has no legally binding Steuerfuss yet).
     */
    @Test
    void communes_endpoint_falls_back_to_latest_seeded_year_for_future_years() throws Exception {
        mockMvc.perform(get("/api/v1/tax/communes")
                        .param("canton", "SG").param("year", "2029")
                        .with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(74))
                .andExpect(jsonPath("$[0].taxYear").value(2026));

        mockMvc.perform(get("/api/v1/tax/communes")
                        .param("canton", "SG").param("year", "2025")
                        .with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(75));
    }

    /**
     * V17 seeds official church multipliers for St. Gallen (BFS 3203 in 2025:
     * roman-catholic 0.26). A roman-catholic tax payer in that commune must
     * see a positive church tax that is part of the total and appears in the
     * breakdown.
     */
    @Test
    void calculate_with_roman_catholic_affiliation_and_seeded_commune_returns_positive_church_tax() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "taxYear", 2025,
                "cantonCode", "SG",
                "bfsNumber", 3203,
                "civilStatus", "SINGLE",
                "numberOfChildren", 0,
                "churchAffiliation", "ROMAN_CATHOLIC",
                "grossEmploymentIncome", 100000
        ));

        String response = mockMvc.perform(post("/api/v1/tax/calculate").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode result = objectMapper.readTree(response);
        BigDecimal churchTax = decimal(result, "churchTax");
        assertThat(churchTax).isGreaterThan(BigDecimal.ZERO);
        assertThat(decimal(result, "totalIncomeTax"))
                .isEqualByComparingTo(decimal(result, "federalTax")
                        .add(decimal(result, "cantonalTax"))
                        .add(decimal(result, "communalTax"))
                        .add(churchTax));

        boolean hasChurchItem = false;
        for (JsonNode item : result.get("breakdown")) {
            if ("Church Tax".equals(item.get("label").asText())) {
                hasChurchItem = true;
            }
        }
        assertThat(hasChurchItem)
                .as("breakdown must contain a 'Church Tax' item when church tax > 0")
                .isTrue();
    }
}
