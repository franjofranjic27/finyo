package ch.finyo.wealth;

import ch.finyo.BaseIntegrationTest;
import ch.finyo.common.SwissTime;
import ch.finyo.investment.InstrumentRepository;
import ch.finyo.investment.PortfolioSnapshotRepository;
import ch.finyo.investment.PositionRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for /api/v1/wealth.
 *
 * PORTFOLIO buckets are backed by positions created with a name only plus a
 * currentPrice override so the SIX client is never invoked (established
 * PortfolioIT pattern). A position named "… Fund" is classified as FUND by
 * the AssetClassifier heuristic.
 */
class WealthIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WealthBucketRepository bucketRepository;

    @Autowired
    private NetWorthSnapshotRepository netWorthSnapshotRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private PortfolioSnapshotRepository portfolioSnapshotRepository;

    @BeforeEach
    void cleanTables() {
        bucketRepository.deleteAll();
        netWorthSnapshotRepository.deleteAll();
        positionRepository.deleteAll();
        portfolioSnapshotRepository.deleteAll();
        instrumentRepository.deleteAll();
    }

    // -------------------------------------------------------------------------
    // Request-body helpers
    // -------------------------------------------------------------------------

    private String manualBucketBody(String name, String manualBalance, String monthlyRate) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("source", "MANUAL");
        if (manualBalance != null) {
            body.put("manualBalance", manualBalance);
        }
        if (monthlyRate != null) {
            body.put("monthlyRate", monthlyRate);
        }
        return objectMapper.writeValueAsString(body);
    }

    private String portfolioBucketBody(String name, List<String> assetClasses) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("source", "PORTFOLIO");
        if (assetClasses != null) {
            body.put("assetClasses", assetClasses);
        }
        return objectMapper.writeValueAsString(body);
    }

    private UUID createBucket(String body) throws Exception {
        String created = mockMvc.perform(post("/api/v1/wealth/buckets").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(created).get("id").asText());
    }

    /** Name-only position with a currentPrice override — the SIX client is never called. */
    private void createFundPosition(String name, String quantity, String price) throws Exception {
        mockMvc.perform(post("/api/v1/positions").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "quantity", quantity,
                                "purchasePrice", price,
                                "currentPrice", price))))
                .andExpect(status().isCreated());
    }

    // -------------------------------------------------------------------------
    // Overview, snapshot upsert & history
    // -------------------------------------------------------------------------

    @Test
    void overview_aggregates_manual_and_portfolio_buckets_and_upserts_todays_snapshot() throws Exception {
        createBucket(manualBucketBody("Cash", "5000", "100"));
        createFundPosition("Broker Fund", "10", "110"); // classified as FUND -> value 1100
        createBucket(portfolioBucketBody("Investments", List.of("FUND")));

        int remainingMonths = WealthOverviewService.monthsRemaining(LocalDate.now(SwissTime.ZONE));
        double cashForecast = 5000 + 100.0 * remainingMonths;

        String response = mockMvc.perform(get("/api/v1/wealth").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets.length()", is(2)))
                .andExpect(jsonPath("$.buckets[0].name", is("Cash")))
                .andExpect(jsonPath("$.buckets[0].source", is("MANUAL")))
                .andExpect(jsonPath("$.buckets[0].balance", is(5000.0)))
                .andExpect(jsonPath("$.buckets[0].sharePct", is(81.97)))
                .andExpect(jsonPath("$.buckets[0].monthlyRate", is(100.0)))
                .andExpect(jsonPath("$.buckets[0].forecastYearEnd", is(cashForecast)))
                .andExpect(jsonPath("$.buckets[1].name", is("Investments")))
                .andExpect(jsonPath("$.buckets[1].source", is("PORTFOLIO")))
                .andExpect(jsonPath("$.buckets[1].assetClasses[0]", is("FUND")))
                .andExpect(jsonPath("$.buckets[1].balance", is(1100.0)))
                .andExpect(jsonPath("$.buckets[1].sharePct", is(18.03)))
                .andExpect(jsonPath("$.total", is(6100.0)))
                .andExpect(jsonPath("$.totalMonthlyRate", is(100.0)))
                .andExpect(jsonPath("$.totalForecastYearEnd", is(cashForecast + 1100.0)))
                .andReturn().getResponse().getContentAsString();

        // no snapshot existed before today -> ytd comparison is not available yet
        JsonNode tree = objectMapper.readTree(response);
        assertThat(tree.path("ytdChange").isNull() || tree.path("ytdChange").isMissingNode()).isTrue();
        assertThat(tree.path("ytdChangePct").isNull() || tree.path("ytdChangePct").isMissingNode()).isTrue();

        // exactly one snapshot for today, carrying the aggregated total
        LocalDate today = LocalDate.now(SwissTime.ZONE);
        assertThat(netWorthSnapshotRepository.count()).isEqualTo(1);
        NetWorthSnapshot snapshot = netWorthSnapshotRepository.findAll().getFirst();
        assertThat(snapshot.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(snapshot.getSnapshotDate()).isEqualTo(today);
        assertThat(snapshot.getTotal()).isEqualByComparingTo("6100");

        // a second read on the same day updates instead of inserting
        mockMvc.perform(get("/api/v1/wealth").with(asUser()))
                .andExpect(status().isOk());
        assertThat(netWorthSnapshotRepository.count()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/wealth/history").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()", is(1)))
                .andExpect(jsonPath("$.points[0].date", is(today.toString())))
                .andExpect(jsonPath("$.points[0].total", is(6100.0)));
    }

    @Test
    void ytd_change_is_computed_against_an_earlier_snapshot_of_the_year() throws Exception {
        LocalDate today = LocalDate.now(SwissTime.ZONE);
        Assumptions.assumeTrue(today.getDayOfYear() > 1, "no prior same-year snapshot possible on Jan 1");

        netWorthSnapshotRepository.save(NetWorthSnapshot.builder()
                .userId(TEST_USER_ID)
                .snapshotDate(today.minusDays(1))
                .total(new BigDecimal("4000"))
                .build());
        createBucket(manualBucketBody("Cash", "5000", null));

        mockMvc.perform(get("/api/v1/wealth").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(5000.0)))
                .andExpect(jsonPath("$.ytdChange", is(1000.0)))
                .andExpect(jsonPath("$.ytdChangePct", is(25.0)));
    }

    @Test
    void overview_without_buckets_returns_zero_totals() throws Exception {
        mockMvc.perform(get("/api/v1/wealth").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets.length()", is(0)))
                .andExpect(jsonPath("$.total", is(0)))
                .andExpect(jsonPath("$.totalMonthlyRate", is(0)))
                .andExpect(jsonPath("$.totalForecastYearEnd", is(0)));
        assertThat(netWorthSnapshotRepository.count()).isZero();
    }

    // -------------------------------------------------------------------------
    // Bucket CRUD & validation
    // -------------------------------------------------------------------------

    @Test
    void create_returns_the_stored_bucket_without_computed_values() throws Exception {
        mockMvc.perform(post("/api/v1/wealth/buckets").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualBucketBody("Cash", "5000", "100")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Cash")))
                .andExpect(jsonPath("$.source", is("MANUAL")))
                .andExpect(jsonPath("$.manualBalance", is(5000)))
                .andExpect(jsonPath("$.monthlyRate", is(100)))
                .andExpect(jsonPath("$.sortOrder", is(0)))
                .andExpect(jsonPath("$.assetClasses.length()", is(0)));
    }

    @Test
    void update_changes_the_stored_fields() throws Exception {
        UUID id = createBucket(manualBucketBody("Cash", "5000", null));

        mockMvc.perform(put("/api/v1/wealth/buckets/{id}", id).with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualBucketBody("Emergency Fund", "7500", "250")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id.toString())))
                .andExpect(jsonPath("$.name", is("Emergency Fund")))
                .andExpect(jsonPath("$.manualBalance", is(7500)))
                .andExpect(jsonPath("$.monthlyRate", is(250)));
    }

    @Test
    void delete_removes_the_bucket() throws Exception {
        UUID id = createBucket(manualBucketBody("Cash", "5000", null));

        mockMvc.perform(delete("/api/v1/wealth/buckets/{id}", id).with(asUser()))
                .andExpect(status().isNoContent());
        assertThat(bucketRepository.count()).isZero();
    }

    @Test
    void manual_bucket_without_balance_is_rejected() throws Exception {
        mockMvc.perform(post("/api/v1/wealth/buckets").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualBucketBody("Cash", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Bad Request")))
                .andExpect(jsonPath("$.detail", containsString("manualBalance")));
    }

    @Test
    void portfolio_bucket_without_asset_classes_is_rejected() throws Exception {
        mockMvc.perform(post("/api/v1/wealth/buckets").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(portfolioBucketBody("Broker", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Bad Request")))
                .andExpect(jsonPath("$.detail", containsString("assetClasses")));
    }

    @Test
    void asset_class_linked_to_another_bucket_is_rejected() throws Exception {
        createBucket(portfolioBucketBody("Broker", List.of("ETF", "STOCK")));

        mockMvc.perform(post("/api/v1/wealth/buckets").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(portfolioBucketBody("Second Broker", List.of("STOCK"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Bad Request")))
                .andExpect(jsonPath("$.detail", containsString("STOCK")))
                .andExpect(jsonPath("$.detail", containsString("Broker")));
    }

    @Test
    void duplicate_bucket_name_is_rejected() throws Exception {
        createBucket(manualBucketBody("Cash", "5000", null));

        mockMvc.perform(post("/api/v1/wealth/buckets").with(asUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualBucketBody("Cash", "1", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Bad Request")))
                .andExpect(jsonPath("$.detail", containsString("Cash")));
    }

    @Test
    void history_rejects_months_outside_the_allowed_range() throws Exception {
        mockMvc.perform(get("/api/v1/wealth/history").with(asUser()).param("months", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Validation Failed")));
        mockMvc.perform(get("/api/v1/wealth/history").with(asUser()).param("months", "61"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // Cross-user isolation & authentication
    // -------------------------------------------------------------------------

    @Test
    void other_user_cannot_see_or_modify_foreign_buckets() throws Exception {
        UUID id = createBucket(manualBucketBody("Cash", "5000", null));

        mockMvc.perform(get("/api/v1/wealth").with(asOtherUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets.length()", is(0)))
                .andExpect(jsonPath("$.total", is(0)));

        mockMvc.perform(put("/api/v1/wealth/buckets/{id}", id).with(asOtherUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualBucketBody("Hijacked", "1", null)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/wealth/buckets/{id}", id).with(asOtherUser()))
                .andExpect(status().isNotFound());
        assertThat(bucketRepository.count()).isEqualTo(1);
    }

    @Test
    void unauthenticated_requests_are_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/wealth"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/wealth/buckets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualBucketBody("Cash", "1", null)))
                .andExpect(status().isUnauthorized());
    }
}
