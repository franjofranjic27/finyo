package ch.finyo.investment;

import ch.finyo.BaseIntegrationTest;
import ch.finyo.common.SwissTime;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for /api/v1/positions and /api/v1/portfolio.
 *
 * All positions are created with a name only (no valor/ISIN/ticker) plus a
 * currentPrice override so the SIX client is never invoked (established
 * InstrumentIT pattern — the Caffeine cache would otherwise leak identifiers
 * across tests and users).
 */
class PortfolioIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private PortfolioSnapshotRepository snapshotRepository;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @BeforeEach
    void cleanTables() {
        positionRepository.deleteAll();
        snapshotRepository.deleteAll();
        instrumentRepository.deleteAll();
    }

    private String positionBody(String name, String quantity, String purchasePrice, String currentPrice) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("quantity", quantity);
        body.put("purchasePrice", purchasePrice);
        if (currentPrice != null) {
            body.put("currentPrice", currentPrice);
        }
        return objectMapper.writeValueAsString(body);
    }

    private UUID createPosition(String name, String quantity, String purchasePrice, String currentPrice)
            throws Exception {
        String created = mockMvc.perform(post("/api/v1/positions").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(positionBody(name, quantity, purchasePrice, currentPrice)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(created).get("id").asText());
    }

    @Test
    void full_lifecycle_position_portfolio_snapshot_history_and_delete() throws Exception {
        UUID positionId = createPosition("Test Fund", "10", "100", "110");

        // The manual currentPrice from position creation is the price shown — sourced as MANUAL,
        // not a market quote. A name-only position has no ISIN, so no provider prices it.
        mockMvc.perform(get("/api/v1/portfolio").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions.length()", is(1)))
                .andExpect(jsonPath("$.positions[0].name", is("Test Fund")))
                .andExpect(jsonPath("$.positions[0].priceSource", is("MANUAL")))
                .andExpect(jsonPath("$.positions[0].currentPrice", is(110.0)))
                .andExpect(jsonPath("$.positions[0].allocationPct", is(100.0)))
                .andExpect(jsonPath("$.totalValue", is(1100.0)))
                .andExpect(jsonPath("$.totalCost", is(1000.0)))
                .andExpect(jsonPath("$.gainLoss", is(100.0)))
                .andExpect(jsonPath("$.returnPct", is(10.0)));

        // The read no longer writes a snapshot — that was a GET mutating state, and it left a
        // hole in the history on every day the user did not log in. The snapshot is a job now.
        assertThat(snapshotRepository.count()).isZero();

        // Drive the snapshot the way PortfolioSnapshotJob does.
        LocalDate today = LocalDate.now(SwissTime.ZONE);
        portfolioService.writeSnapshot(TEST_USER_ID);
        assertThat(snapshotRepository.count()).isEqualTo(1);
        PortfolioSnapshot snapshot = snapshotRepository
                .findByUserIdAndSnapshotDate(TEST_USER_ID, today).orElseThrow();
        assertThat(snapshot.getTotalValue()).isEqualByComparingTo("1100");
        assertThat(snapshot.getTotalCost()).isEqualByComparingTo("1000");

        // Running it again the same day updates rather than inserts.
        portfolioService.writeSnapshot(TEST_USER_ID);
        assertThat(snapshotRepository.count()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/portfolio/history").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()", is(1)))
                .andExpect(jsonPath("$.points[0].totalValue", is(1100.0)))
                .andExpect(jsonPath("$.points[0].date", is(today.toString())));

        mockMvc.perform(delete("/api/v1/positions/{id}", positionId).with(asUser()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/portfolio").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions.length()", is(0)))
                .andExpect(jsonPath("$.totalValue", is(0)));
    }

    @Test
    void bulk_import_imports_valid_rows_and_reports_row_errors() throws Exception {
        String bulkBody = objectMapper.writeValueAsString(Map.of("positions", List.of(
                Map.of("name", "Fund A", "quantity", "5", "purchasePrice", "20", "currentPrice", "25"),
                Map.of("quantity", "1", "purchasePrice", "1"), // no name/isin/valor
                Map.of("name", "Fund C", "quantity", "2", "purchasePrice", "50"))));

        mockMvc.perform(post("/api/v1/positions/bulk").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imported", is(2)))
                .andExpect(jsonPath("$.failed", is(1)))
                .andExpect(jsonPath("$.errors.length()", is(1)))
                .andExpect(jsonPath("$.errors[0]", is("Row 2: At least one of name, isin or valor must be provided")));

        // Fund C has no price at all -> purchase-price fallback
        mockMvc.perform(get("/api/v1/portfolio").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions.length()", is(2)))
                .andExpect(jsonPath("$.positions[?(@.name == 'Fund C')].priceSource", is(List.of("PURCHASE"))))
                .andExpect(jsonPath("$.totalValue", is(225.0)))  // 5x25 + 2x50
                .andExpect(jsonPath("$.totalCost", is(200.0)));
    }

    @Test
    void other_user_cannot_delete_a_foreign_position() throws Exception {
        UUID positionId = createPosition("Owner Fund", "1", "10", "10");

        mockMvc.perform(delete("/api/v1/positions/{id}", positionId).with(asOtherUser()))
                .andExpect(status().isNotFound());
        assertThat(positionRepository.count()).isEqualTo(1);
    }

    @Test
    void create_rejects_non_positive_quantity() throws Exception {
        mockMvc.perform(post("/api/v1/positions").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(positionBody("Fund", "0", "10", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Validation Failed")));
    }

    @Test
    void create_rejects_a_position_without_name_isin_and_valor() throws Exception {
        mockMvc.perform(post("/api/v1/positions").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(positionBody(null, "1", "10", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Bad Request")));
    }

    @Test
    void history_rejects_months_outside_the_allowed_range() throws Exception {
        mockMvc.perform(get("/api/v1/portfolio/history").with(asUser()).param("months", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Validation Failed")));
        mockMvc.perform(get("/api/v1/portfolio/history").with(asUser()).param("months", "61"))
                .andExpect(status().isBadRequest());
    }
}
