package ch.finyo.pillar3;

import ch.finyo.BaseIntegrationTest;
import ch.finyo.common.money.CurrencyCode;
import ch.finyo.marketdata.InstrumentPrice;
import ch.finyo.marketdata.InstrumentPriceRepository;
import ch.finyo.marketdata.spi.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the pillar 3a product catalog:
 *   - /api/v1/pillar3/products (user catalog + compare)
 *   - /api/v1/admin/pillar3/products (admin CRUD + bulk import)
 *
 * V18 creates the table without seed data, so every test starts from an
 * empty catalog (deleteAll in @BeforeEach) and seeds exactly what it needs.
 */
class Pillar3ProductIT extends BaseIntegrationTest {

    private static final String PRODUCTS_URL = "/api/v1/pillar3/products";
    private static final String COMPARE_URL = "/api/v1/pillar3/products/compare";
    private static final String ADMIN_URL = "/api/v1/admin/pillar3/products";
    private static final String IMPORT_URL = ADMIN_URL + "/import";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Pillar3ProductRepository productRepository;

    @Autowired
    private InstrumentPriceRepository instrumentPriceRepository;

    @BeforeEach
    void cleanProducts() {
        productRepository.deleteAll();
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private Pillar3Product saveProduct(String name, String isin, String equityPct, String terPct,
                                       boolean active, int sortOrder) {
        return productRepository.save(Pillar3Product.builder()
                .provider("Test Provider")
                .name(name)
                .isin(isin)
                .valor("1234567")
                .equityPct(new BigDecimal(equityPct))
                .terPct(new BigDecimal(terPct))
                .active(active)
                .sortOrder(sortOrder)
                .build());
    }

    private Pillar3ProductRequest productRequest(String name, String isin) {
        return new Pillar3ProductRequest("UBS", name, isin, "1234567",
                new BigDecimal("100"), new BigDecimal("0.50"), null, null);
    }

    private String productJson(String name, String isin) {
        return objectMapper.writeValueAsString(productRequest(name, isin));
    }

    private String importJson(Pillar3ProductRequest... rows) {
        return objectMapper.writeValueAsString(new Pillar3ProductImportRequest(List.of(rows)));
    }

    private String compareJson(String currentBalance, String annualContribution, int years, UUID... productIds) {
        return objectMapper.writeValueAsString(new Pillar3CompareRequest(
                new BigDecimal(currentBalance), new BigDecimal(annualContribution), years, List.of(productIds)));
    }

    // =========================================================================
    // User catalog: GET /api/v1/pillar3/products
    // =========================================================================

    @Test
    void user_catalog_lists_only_active_products_sorted_by_sort_order() throws Exception {
        saveProduct("Beta Fund", "CH0000000016", "100", "0.50", true, 2);
        saveProduct("Alpha Fund", "CH0000000024", "45", "0.45", true, 1);
        saveProduct("Hidden Fund", "CH0000000032", "0", "0.10", false, 0);

        mockMvc.perform(get(PRODUCTS_URL).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)))
                .andExpect(jsonPath("$[0].name", is("Alpha Fund")))
                .andExpect(jsonPath("$[1].name", is("Beta Fund")));
    }

    @Test
    void user_catalog_exposes_the_derived_return_percentages() throws Exception {
        // 45% equity → gross 1 + 45 × 0.05 = 3.25%, net 3.25 − 0.45 = 2.80%
        saveProduct("Balanced Fund", "CH0000000024", "45", "0.45", true, 0);

        String response = mockMvc.perform(get(PRODUCTS_URL).with(asUser()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode product = objectMapper.readTree(response).get(0);
        assertThat(product.get("expectedReturnPct").decimalValue()).isEqualByComparingTo("3.25");
        assertThat(product.get("netReturnPct").decimalValue()).isEqualByComparingTo("2.80");
    }

    @Test
    void user_catalog_search_matches_a_name_fragment_case_insensitively() throws Exception {
        saveProduct("UBS Vitainvest 100", "CH0000000016", "100", "0.50", true, 0);
        saveProduct("Swisscanto Sustainable 45", "CH0000000024", "45", "0.45", true, 1);

        mockMvc.perform(get(PRODUCTS_URL).with(asUser()).param("search", "vitaINVEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].name", is("UBS Vitainvest 100")));
    }

    @Test
    void user_catalog_search_matches_an_isin_fragment_case_insensitively() throws Exception {
        saveProduct("UBS Vitainvest 100", "CH0111111115", "100", "0.50", true, 0);
        saveProduct("Swisscanto Sustainable 45", "CH0222222224", "45", "0.45", true, 1);

        mockMvc.perform(get(PRODUCTS_URL).with(asUser()).param("search", "ch0111"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].isin", is("CH0111111115")));
    }

    // =========================================================================
    // Price history: GET /api/v1/pillar3/products/{id}/price-history
    // =========================================================================

    private void storeClose(String isin, String close, LocalDate date) {
        instrumentPriceRepository.save(InstrumentPrice.builder()
                .isin(isin)
                .priceDate(date)
                .close(new BigDecimal(close))
                .currency(new CurrencyCode("CHF"))
                .source(DataSource.SIX)
                .retrievedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
    }

    @Test
    void price_history_returns_the_stored_closes_oldest_first_with_the_currency() throws Exception {
        Pillar3Product fund = saveProduct("Vitainvest 100", "CH0333333336", "100", "0.50", true, 0);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        // stored out of order on purpose — the endpoint has to return them oldest first
        storeClose("CH0333333336", "142.00", today.minusDays(1));
        storeClose("CH0333333336", "140.00", today.minusDays(2));
        storeClose("CH0333333336", "145.10", today);

        mockMvc.perform(get(PRODUCTS_URL + "/{id}/price-history", fund.getId()).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isin", is("CH0333333336")))
                .andExpect(jsonPath("$.currency", is("CHF")))
                .andExpect(jsonPath("$.points.length()", is(3)))
                .andExpect(jsonPath("$.points[0].date", is(today.minusDays(2).toString())))
                .andExpect(jsonPath("$.points[0].close", is(140.0)))
                .andExpect(jsonPath("$.points[1].date", is(today.minusDays(1).toString())))
                .andExpect(jsonPath("$.points[2].date", is(today.toString())))
                .andExpect(jsonPath("$.points[2].close", is(145.1)));
    }

    @Test
    void price_history_of_an_unlisted_fund_is_empty_rather_than_an_error() throws Exception {
        // No stored closes and no providers in the test profile: the response is empty and the
        // fire-and-forget backfill the request triggers stores nothing.
        Pillar3Product fund = saveProduct("Unlisted 3a Fund", "CH0444444444", "45", "0.45", true, 0);

        mockMvc.perform(get(PRODUCTS_URL + "/{id}/price-history", fund.getId()).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isin", is("CH0444444444")))
                .andExpect(jsonPath("$.currency", nullValue()))
                .andExpect(jsonPath("$.points.length()", is(0)));
    }

    @Test
    void price_history_resolves_a_deactivated_product_too() throws Exception {
        // Saved scenarios keep referencing deactivated products, so the detail view must resolve.
        Pillar3Product fund = saveProduct("Retired Fund", "CH0555555553", "45", "0.45", false, 0);
        storeClose("CH0555555553", "101.00", LocalDate.now(ZoneOffset.UTC));

        mockMvc.perform(get(PRODUCTS_URL + "/{id}/price-history", fund.getId()).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()", is(1)))
                .andExpect(jsonPath("$.points[0].close", is(101.0)));
    }

    @Test
    void price_history_of_an_unknown_product_returns_404_naming_the_id() throws Exception {
        UUID unknownId = UUID.randomUUID();

        mockMvc.perform(get(PRODUCTS_URL + "/{id}/price-history", unknownId).with(asUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", containsString(unknownId.toString())));
    }

    // =========================================================================
    // Compare: POST /api/v1/pillar3/products/compare
    // =========================================================================

    @Test
    void compare_sorts_products_by_final_capital_and_reports_totals() throws Exception {
        // high (equity 100, TER 0.50): Y1: 1000 × 1.06 = 1060.00, fee 5.30, +500 → 1554.70
        //                              Y2: 1554.70 × 1.06 = 1647.98, fee 8.24, +500 → 2139.74
        // low  (equity 0, TER 0):      Y1: 1010.00 + 500 = 1510.00
        //                              Y2: 1510.00 × 1.01 = 1525.10 + 500 = 2025.10
        Pillar3Product high = saveProduct("High Equity", "CH0000000016", "100", "0.50", true, 0);
        Pillar3Product low = saveProduct("Money Market", "CH0000000024", "0", "0.000", true, 1);

        String response = mockMvc.perform(post(COMPARE_URL).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compareJson("1000", "500", 2, low.getId(), high.getId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode result = objectMapper.readTree(response);
        assertThat(result.get("totalPaidIn").decimalValue()).isEqualByComparingTo("2000.00");
        assertThat(result.get("maxAnnualContribution").decimalValue()).isEqualByComparingTo("7258");
        JsonNode products = result.get("products");
        assertThat(products.size()).isEqualTo(2);
        assertThat(products.get(0).get("productId").asText()).isEqualTo(high.getId().toString());
        assertThat(products.get(0).get("finalCapital").decimalValue()).isEqualByComparingTo("2139.74");
        assertThat(products.get(0).get("totalFees").decimalValue()).isEqualByComparingTo("13.54");
        assertThat(products.get(1).get("productId").asText()).isEqualTo(low.getId().toString());
        assertThat(products.get(1).get("finalCapital").decimalValue()).isEqualByComparingTo("2025.10");
    }

    @Test
    void compare_rejects_an_annual_contribution_above_the_legal_maximum() throws Exception {
        Pillar3Product fund = saveProduct("Fund", "CH0000000016", "45", "0.45", true, 0);

        mockMvc.perform(post(COMPARE_URL).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compareJson("0", "8000", 10, fund.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Validation Failed")))
                .andExpect(jsonPath("$.detail", containsString("annualContribution")));
    }

    @Test
    void compare_rejects_an_empty_product_selection() throws Exception {
        mockMvc.perform(post(COMPARE_URL).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compareJson("0", "7000", 10)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Validation Failed")));
    }

    @Test
    void compare_with_an_unknown_product_id_returns_404_naming_the_id() throws Exception {
        Pillar3Product known = saveProduct("Fund", "CH0000000016", "45", "0.45", true, 0);
        UUID unknownId = UUID.randomUUID();

        mockMvc.perform(post(COMPARE_URL).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compareJson("0", "7000", 10, known.getId(), unknownId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", containsString(unknownId.toString())));
    }

    // =========================================================================
    // Admin CRUD: /api/v1/admin/pillar3/products
    // =========================================================================

    @Test
    void admin_crud_lifecycle_covers_create_list_deactivate_and_delete() throws Exception {
        // Create
        String created = mockMvc.perform(post(ADMIN_URL).with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Vitainvest 100", "CH0000000016")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.active", is(true)))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).get("id").asText();

        // Admin list contains it
        mockMvc.perform(get(ADMIN_URL).with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)))
                .andExpect(jsonPath("$[0].id", is(id)));

        // Update: rename + deactivate
        String update = objectMapper.writeValueAsString(new Pillar3ProductRequest(
                "UBS", "Renamed Fund", "CH0000000016", "1234567",
                new BigDecimal("100"), new BigDecimal("0.50"), false, 7));
        mockMvc.perform(put(ADMIN_URL + "/{id}", id).with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Renamed Fund")))
                .andExpect(jsonPath("$.active", is(false)))
                .andExpect(jsonPath("$.sortOrder", is(7)));

        // Deactivated → hidden from the user catalog, still visible to admins
        mockMvc.perform(get(PRODUCTS_URL).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
        mockMvc.perform(get(ADMIN_URL).with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(1)));

        // Delete
        mockMvc.perform(delete(ADMIN_URL + "/{id}", id).with(asAdmin()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(ADMIN_URL).with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
    }

    @Test
    void create_with_a_duplicate_isin_returns_409_even_for_case_variants() throws Exception {
        mockMvc.perform(post(ADMIN_URL).with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Original", "CH0000000016")))
                .andExpect(status().isCreated());

        mockMvc.perform(post(ADMIN_URL).with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Copycat", "ch0000000016")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title", is("Conflict")))
                .andExpect(jsonPath("$.detail", containsString("CH0000000016")));
    }

    @Test
    void update_of_an_unknown_product_returns_404() throws Exception {
        UUID unknownId = UUID.randomUUID();

        mockMvc.perform(put(ADMIN_URL + "/{id}", unknownId).with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Ghost Fund", "CH0000000016")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", containsString(unknownId.toString())));
    }

    @Test
    void create_with_a_malformed_isin_returns_400() throws Exception {
        mockMvc.perform(post(ADMIN_URL).with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Broken Fund", "INVALID")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Validation Failed")))
                .andExpect(jsonPath("$.detail", containsString("isin")));
    }

    // =========================================================================
    // Security: every admin endpoint must be forbidden for plain users
    // =========================================================================

    @Test
    void admin_list_is_forbidden_for_a_plain_user() throws Exception {
        mockMvc.perform(get(ADMIN_URL).with(asUser()))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_create_is_forbidden_for_a_plain_user() throws Exception {
        mockMvc.perform(post(ADMIN_URL).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Fund", "CH0000000016")))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_update_is_forbidden_for_a_plain_user() throws Exception {
        mockMvc.perform(put(ADMIN_URL + "/{id}", UUID.randomUUID()).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Fund", "CH0000000016")))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_delete_is_forbidden_for_a_plain_user() throws Exception {
        mockMvc.perform(delete(ADMIN_URL + "/{id}", UUID.randomUUID()).with(asUser()))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_import_is_forbidden_for_a_plain_user() throws Exception {
        mockMvc.perform(post(IMPORT_URL).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importJson(productRequest("Fund", "CH0000000016"))))
                .andExpect(status().isForbidden());
        assertThat(productRepository.count()).isZero();
    }

    @Test
    void user_catalog_and_compare_are_accessible_with_the_user_role() throws Exception {
        Pillar3Product fund = saveProduct("Fund", "CH0000000016", "45", "0.45", true, 0);

        mockMvc.perform(get(PRODUCTS_URL).with(asUser()))
                .andExpect(status().isOk());
        mockMvc.perform(post(COMPARE_URL).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compareJson("1000", "7000", 10, fund.getId())))
                .andExpect(status().isOk());
    }

    @Test
    void admin_list_is_accessible_with_the_admin_role() throws Exception {
        mockMvc.perform(get(ADMIN_URL).with(asAdmin()))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // Import: POST /api/v1/admin/pillar3/products/import
    // =========================================================================

    @Test
    void import_creates_new_rows_and_a_reimport_updates_them_idempotently() throws Exception {
        String payload = importJson(
                productRequest("Fund One", "CH0000000016"),
                productRequest("Fund Two", "CH0000000024"),
                productRequest("Fund Three", "CH0000000032"));

        mockMvc.perform(post(IMPORT_URL).with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows", is(3)))
                .andExpect(jsonPath("$.created", is(3)))
                .andExpect(jsonPath("$.updated", is(0)))
                .andExpect(jsonPath("$.failed", is(0)));

        mockMvc.perform(post(IMPORT_URL).with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(0)))
                .andExpect(jsonPath("$.updated", is(3)))
                .andExpect(jsonPath("$.failed", is(0)));

        assertThat(productRepository.count()).isEqualTo(3);
    }

    @Test
    void import_updates_the_existing_product_when_the_isin_differs_only_by_case() throws Exception {
        saveProduct("Old Name", "CH0000000016", "45", "0.45", true, 0);

        mockMvc.perform(post(IMPORT_URL).with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importJson(productRequest("New Name", "ch0000000016"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created", is(0)))
                .andExpect(jsonPath("$.updated", is(1)));

        assertThat(productRepository.count()).isEqualTo(1);
        assertThat(productRepository.findByIsin("CH0000000016"))
                .hasValueSatisfying(product -> assertThat(product.getName()).isEqualTo("New Name"));
    }

    @Test
    void import_isolates_an_invalid_row_and_imports_the_rest() throws Exception {
        mockMvc.perform(post(IMPORT_URL).with(asAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(importJson(
                                productRequest("Valid One", "CH0000000016"),
                                productRequest("Broken", "NOT_AN_ISIN"),
                                productRequest("Valid Two", "CH0000000024"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows", is(3)))
                .andExpect(jsonPath("$.created", is(2)))
                .andExpect(jsonPath("$.failed", is(1)))
                .andExpect(jsonPath("$.errors.length()", is(1)))
                .andExpect(jsonPath("$.errors[0]", containsString("row 2")));

        assertThat(productRepository.count()).isEqualTo(2);
    }
}
