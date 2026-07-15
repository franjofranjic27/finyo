package ch.finyo.wealth;

import ch.finyo.common.SwissTime;
import ch.finyo.investment.AssetClass;
import ch.finyo.investment.PortfolioPositionResponse;
import ch.finyo.investment.PortfolioResponse;
import ch.finyo.investment.PortfolioService;
import ch.finyo.investment.PriceSource;
import ch.finyo.pillar3.Pillar3ScenarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * Unit tests for WealthOverviewService.
 *
 * Focus areas:
 *   1. Balance resolution: MANUAL from the stored balance, PORTFOLIO as the
 *      sum of positions in the linked asset classes (single portfolio read).
 *   2. Share and year-end forecast math incl. the zero guards.
 *   3. PILLAR3 balance resolution from the default pillar 3a scenario,
 *      including the lazy repository read.
 *   4. Year-to-date change against the earliest snapshot of the current year.
 *   5. Snapshot upsert: exactly one per overview read, none without buckets.
 */
@ExtendWith(MockitoExtension.class)
class WealthOverviewServiceTest {

    private static final String USER_ID = "user-wealth-1";
    private static final LocalDate TODAY = LocalDate.now(SwissTime.ZONE);
    private static final int REMAINING_MONTHS = WealthOverviewService.monthsRemaining(TODAY);

    @Mock
    private WealthBucketRepository bucketRepository;

    @Mock
    private NetWorthSnapshotRepository snapshotRepository;

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private Pillar3ScenarioService pillar3ScenarioService;

    @InjectMocks
    private WealthOverviewService overviewService;

    // -------------------------------------------------------------------------
    // Builders & stubbing helpers
    // -------------------------------------------------------------------------

    private static WealthBucket manualBucket(String name, String balance, String monthlyRate, int sortOrder) {
        return WealthBucket.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .name(name)
                .source(WealthSource.MANUAL)
                .manualBalance(new BigDecimal(balance))
                .monthlyRate(new BigDecimal(monthlyRate))
                .sortOrder(sortOrder)
                .build();
    }

    private static WealthBucket portfolioBucket(String name, String assetClasses) {
        return WealthBucket.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .name(name)
                .source(WealthSource.PORTFOLIO)
                .assetClasses(assetClasses)
                .monthlyRate(BigDecimal.ZERO)
                .sortOrder(0)
                .build();
    }

    private static WealthBucket pillar3Bucket(String name) {
        return WealthBucket.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .name(name)
                .source(WealthSource.PILLAR3)
                .monthlyRate(BigDecimal.ZERO)
                .sortOrder(0)
                .build();
    }

    private static PortfolioPositionResponse position(AssetClass assetClass, String value) {
        UUID id = UUID.randomUUID();
        return new PortfolioPositionResponse(id, id, UUID.randomUUID(), assetClass,
                "Position " + assetClass, null, null, "CHF",
                BigDecimal.ONE, BigDecimal.ONE, null, BigDecimal.ONE, PriceSource.MARKET, null, false,
                new BigDecimal(value), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private void givenPortfolio(PortfolioPositionResponse... positions) {
        BigDecimal total = List.of(positions).stream()
                .map(PortfolioPositionResponse::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        given(portfolioService.getPortfolio(USER_ID)).willReturn(new PortfolioResponse(
                List.of(positions), total, total, BigDecimal.ZERO, BigDecimal.ZERO,
                OffsetDateTime.now(ZoneOffset.UTC)));
    }

    private void givenNoYtdBaseline() {
        given(snapshotRepository
                .findFirstByUserIdAndSnapshotDateGreaterThanEqualAndSnapshotDateLessThanOrderBySnapshotDateAsc(
                        eq(USER_ID), any(), any()))
                .willReturn(Optional.empty());
    }

    // -------------------------------------------------------------------------
    // Balance resolution & aggregation
    // -------------------------------------------------------------------------

    @Test
    void resolves_manual_and_portfolio_balances_with_a_single_portfolio_read() {
        given(bucketRepository.findByUserIdOrderBySortOrderAscNameAsc(USER_ID)).willReturn(List.of(
                manualBucket("Cash", "5000", "500", 0),
                portfolioBucket("Broker", "ETF,STOCK")));
        givenPortfolio(
                position(AssetClass.ETF, "1000"),
                position(AssetClass.STOCK, "2000"),
                position(AssetClass.FUND, "700")); // FUND is not linked to any bucket
        givenNoYtdBaseline();

        WealthOverviewResponse overview = overviewService.getOverview(USER_ID);

        assertThat(overview.total()).isEqualByComparingTo("8000");
        assertThat(overview.buckets()).hasSize(2);

        WealthBucketOverviewResponse cash = overview.buckets().get(0);
        assertThat(cash.balance()).isEqualByComparingTo("5000");
        assertThat(cash.sharePct()).isEqualByComparingTo("62.50");

        WealthBucketOverviewResponse broker = overview.buckets().get(1);
        assertThat(broker.balance()).isEqualByComparingTo("3000");
        assertThat(broker.sharePct()).isEqualByComparingTo("37.50");

        then(portfolioService).should().getPortfolio(USER_ID);
        then(portfolioService).shouldHaveNoMoreInteractions();
    }

    @Test
    void portfolio_bucket_without_matching_positions_has_zero_balance() {
        given(bucketRepository.findByUserIdOrderBySortOrderAscNameAsc(USER_ID))
                .willReturn(List.of(portfolioBucket("Crypto", "CRYPTO")));
        givenPortfolio(position(AssetClass.ETF, "1000"));
        givenNoYtdBaseline();

        WealthOverviewResponse overview = overviewService.getOverview(USER_ID);

        assertThat(overview.total()).isEqualByComparingTo("0");
        assertThat(overview.buckets().getFirst().balance()).isEqualByComparingTo("0");
        // zero guard: no division by a zero total
        assertThat(overview.buckets().getFirst().sharePct()).isEqualByComparingTo("0");
    }

    @Test
    void portfolio_is_not_read_when_only_manual_buckets_exist() {
        given(bucketRepository.findByUserIdOrderBySortOrderAscNameAsc(USER_ID))
                .willReturn(List.of(manualBucket("Cash", "100", "0", 0)));
        givenNoYtdBaseline();

        overviewService.getOverview(USER_ID);

        then(portfolioService).shouldHaveNoInteractions();
    }

    // -------------------------------------------------------------------------
    // PILLAR3 balance resolution
    // -------------------------------------------------------------------------

    @Test
    void pillar3_bucket_resolves_the_default_scenarios_current_balance() {
        given(bucketRepository.findByUserIdOrderBySortOrderAscNameAsc(USER_ID)).willReturn(List.of(
                manualBucket("Cash", "5000", "0", 0),
                pillar3Bucket("Säule 3a")));
        given(pillar3ScenarioService.getDefaultCurrentBalance(USER_ID))
                .willReturn(Optional.of(new BigDecimal("25000")));
        givenNoYtdBaseline();

        WealthOverviewResponse overview = overviewService.getOverview(USER_ID);

        WealthBucketOverviewResponse pillar3 = overview.buckets().get(1);
        assertThat(pillar3.balance()).isEqualByComparingTo("25000");
        assertThat(overview.total()).isEqualByComparingTo("30000");
        then(portfolioService).shouldHaveNoInteractions();
    }

    @Test
    void pillar3_bucket_without_a_default_scenario_has_zero_balance() {
        given(bucketRepository.findByUserIdOrderBySortOrderAscNameAsc(USER_ID))
                .willReturn(List.of(pillar3Bucket("Säule 3a")));
        given(pillar3ScenarioService.getDefaultCurrentBalance(USER_ID))
                .willReturn(Optional.empty());
        givenNoYtdBaseline();

        WealthOverviewResponse overview = overviewService.getOverview(USER_ID);

        assertThat(overview.buckets().getFirst().balance()).isEqualByComparingTo("0");
        assertThat(overview.total()).isEqualByComparingTo("0");
    }

    @Test
    void pillar3_scenarios_are_not_read_without_a_pillar3_bucket() {
        given(bucketRepository.findByUserIdOrderBySortOrderAscNameAsc(USER_ID))
                .willReturn(List.of(manualBucket("Cash", "100", "0", 0)));
        givenNoYtdBaseline();

        overviewService.getOverview(USER_ID);

        then(pillar3ScenarioService).shouldHaveNoInteractions();
    }

    // -------------------------------------------------------------------------
    // Forecast math
    // -------------------------------------------------------------------------

    @Test
    void months_remaining_counts_full_months_until_year_end() {
        assertThat(WealthOverviewService.monthsRemaining(LocalDate.of(2026, 1, 15))).isEqualTo(11);
        assertThat(WealthOverviewService.monthsRemaining(LocalDate.of(2026, 7, 11))).isEqualTo(5);
        assertThat(WealthOverviewService.monthsRemaining(LocalDate.of(2026, 12, 31))).isZero();
    }

    @Test
    void forecast_adds_the_monthly_rate_for_each_remaining_month() {
        given(bucketRepository.findByUserIdOrderBySortOrderAscNameAsc(USER_ID)).willReturn(List.of(
                manualBucket("Cash", "5000", "500", 0),
                manualBucket("Pillar 3a", "10000", "0", 1)));
        givenNoYtdBaseline();

        WealthOverviewResponse overview = overviewService.getOverview(USER_ID);

        BigDecimal expectedCashForecast = new BigDecimal("5000")
                .add(new BigDecimal("500").multiply(BigDecimal.valueOf(REMAINING_MONTHS)));
        assertThat(overview.buckets().get(0).forecastYearEnd()).isEqualByComparingTo(expectedCashForecast);
        assertThat(overview.buckets().get(1).forecastYearEnd()).isEqualByComparingTo("10000");
        assertThat(overview.totalMonthlyRate()).isEqualByComparingTo("500");
        assertThat(overview.totalForecastYearEnd())
                .isEqualByComparingTo(expectedCashForecast.add(new BigDecimal("10000")));
    }

    // -------------------------------------------------------------------------
    // Year-to-date change
    // -------------------------------------------------------------------------

    @Test
    void ytd_change_is_computed_against_the_earliest_snapshot_of_the_year() {
        given(bucketRepository.findByUserIdOrderBySortOrderAscNameAsc(USER_ID))
                .willReturn(List.of(manualBucket("Cash", "8000", "0", 0)));
        given(snapshotRepository
                .findFirstByUserIdAndSnapshotDateGreaterThanEqualAndSnapshotDateLessThanOrderBySnapshotDateAsc(
                        eq(USER_ID), eq(LocalDate.of(TODAY.getYear(), 1, 1)), eq(TODAY)))
                .willReturn(Optional.of(NetWorthSnapshot.builder()
                        .userId(USER_ID)
                        .snapshotDate(LocalDate.of(TODAY.getYear(), 1, 1))
                        .total(new BigDecimal("6000"))
                        .build()));

        WealthOverviewResponse overview = overviewService.getOverview(USER_ID);

        assertThat(overview.ytdChange()).isEqualByComparingTo("2000");
        assertThat(overview.ytdChangePct()).isEqualByComparingTo("33.33");
    }

    @Test
    void ytd_fields_are_null_without_a_prior_snapshot() {
        given(bucketRepository.findByUserIdOrderBySortOrderAscNameAsc(USER_ID))
                .willReturn(List.of(manualBucket("Cash", "8000", "0", 0)));
        givenNoYtdBaseline();

        WealthOverviewResponse overview = overviewService.getOverview(USER_ID);

        assertThat(overview.ytdChange()).isNull();
        assertThat(overview.ytdChangePct()).isNull();
    }

    // -------------------------------------------------------------------------
    // Snapshot upsert & empty state
    // -------------------------------------------------------------------------

    @Test
    void overview_upserts_todays_snapshot_with_the_total_at_four_decimals() {
        given(bucketRepository.findByUserIdOrderBySortOrderAscNameAsc(USER_ID))
                .willReturn(List.of(manualBucket("Cash", "5000.5", "0", 0)));
        givenNoYtdBaseline();

        overviewService.getOverview(USER_ID);

        then(snapshotRepository).should().upsert(USER_ID, TODAY, new BigDecimal("5000.5000"));
    }

    @Test
    void empty_bucket_list_yields_zero_totals_and_no_snapshot() {
        given(bucketRepository.findByUserIdOrderBySortOrderAscNameAsc(USER_ID)).willReturn(List.of());

        WealthOverviewResponse overview = overviewService.getOverview(USER_ID);

        assertThat(overview.buckets()).isEmpty();
        assertThat(overview.total()).isEqualByComparingTo("0");
        assertThat(overview.totalMonthlyRate()).isEqualByComparingTo("0");
        assertThat(overview.totalForecastYearEnd()).isEqualByComparingTo("0");
        assertThat(overview.ytdChange()).isNull();
        assertThat(overview.ytdChangePct()).isNull();
        then(snapshotRepository).shouldHaveNoInteractions();
        then(portfolioService).shouldHaveNoInteractions();
    }

    // -------------------------------------------------------------------------
    // History
    // -------------------------------------------------------------------------

    @Test
    void history_maps_snapshots_to_ascending_points() {
        NetWorthSnapshot older = NetWorthSnapshot.builder()
                .userId(USER_ID).snapshotDate(TODAY.minusDays(2)).total(new BigDecimal("100")).build();
        NetWorthSnapshot newer = NetWorthSnapshot.builder()
                .userId(USER_ID).snapshotDate(TODAY).total(new BigDecimal("200")).build();
        given(snapshotRepository.findByUserIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(
                USER_ID, TODAY.minusMonths(12)))
                .willReturn(List.of(older, newer));

        NetWorthHistoryResponse history = overviewService.getHistory(USER_ID, 12);

        assertThat(history.points()).containsExactly(
                new NetWorthHistoryPoint(TODAY.minusDays(2), new BigDecimal("100")),
                new NetWorthHistoryPoint(TODAY, new BigDecimal("200")));
    }
}
