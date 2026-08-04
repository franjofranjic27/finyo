package ch.finyo.wealth;

import ch.finyo.common.SwissTime;
import ch.finyo.investment.AssetClass;
import ch.finyo.investment.PortfolioPositionResponse;
import ch.finyo.investment.PortfolioResponse;
import ch.finyo.investment.PortfolioService;
import ch.finyo.investment.PriceSource;
import ch.finyo.pillar3.Pillar3DefaultScenarioSummary;
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
 *   1. Auto rows: the portfolio row appears iff the user holds a position and
 *      carries the portfolio's CHF total; the pillar 3a row appears iff a
 *      default scenario exists and derives its monthly rate from the annual
 *      contribution.
 *   2. Manual rows follow the auto rows; share and forecast math incl. the
 *      zero guards, with totals spanning auto + manual.
 *   3. Year-to-date change against the earliest snapshot of the current year.
 *   4. Snapshot upsert: exactly one per overview read, none without rows.
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

    private static PortfolioPositionResponse position(AssetClass assetClass, String value) {
        UUID id = UUID.randomUUID();
        BigDecimal amount = new BigDecimal(value);
        return new PortfolioPositionResponse(id, id, UUID.randomUUID(), assetClass,
                "Position " + assetClass, null, null, "CHF",
                BigDecimal.ONE, BigDecimal.ONE, null, BigDecimal.ONE, PriceSource.MARKET, null, false,
                amount, amount, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null);
    }

    private void givenPortfolio(PortfolioPositionResponse... positions) {
        BigDecimal total = List.of(positions).stream()
                .map(PortfolioPositionResponse::valueChf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        given(portfolioService.getPortfolio(USER_ID)).willReturn(new PortfolioResponse(
                List.of(positions), total, total, BigDecimal.ZERO, BigDecimal.ZERO,
                "CHF", false, OffsetDateTime.now(ZoneOffset.UTC)));
    }

    private void givenEmptyPortfolio() {
        givenPortfolio();
    }

    private void givenDefaultScenario(String currentBalance, String annualContribution) {
        given(pillar3ScenarioService.getDefaultScenarioSummary(USER_ID))
                .willReturn(Optional.of(new Pillar3DefaultScenarioSummary(
                        new BigDecimal(currentBalance), new BigDecimal(annualContribution))));
    }

    private void givenNoDefaultScenario() {
        given(pillar3ScenarioService.getDefaultScenarioSummary(USER_ID)).willReturn(Optional.empty());
    }

    private void givenManualBuckets(WealthBucket... buckets) {
        given(bucketRepository.findByUserIdOrderBySortOrderAscNameAsc(USER_ID))
                .willReturn(List.of(buckets));
    }

    private void givenNoYtdBaseline() {
        given(snapshotRepository
                .findFirstByUserIdAndSnapshotDateGreaterThanEqualAndSnapshotDateLessThanOrderBySnapshotDateAsc(
                        eq(USER_ID), any(), any()))
                .willReturn(Optional.empty());
    }

    // -------------------------------------------------------------------------
    // Auto portfolio row
    // -------------------------------------------------------------------------

    @Test
    void portfolio_positions_yield_a_read_only_auto_row_before_the_manual_buckets() {
        givenPortfolio(position(AssetClass.ETF, "1000"), position(AssetClass.STOCK, "2000"));
        givenNoDefaultScenario();
        givenManualBuckets(manualBucket("Cash", "5000", "500", 0));
        givenNoYtdBaseline();

        WealthOverviewResponse overview = overviewService.getOverview(USER_ID);

        assertThat(overview.buckets()).hasSize(2);
        WealthBucketOverviewResponse portfolio = overview.buckets().getFirst();
        assertThat(portfolio.id()).isEqualTo(WealthOverviewService.AUTO_PORTFOLIO_ID);
        assertThat(portfolio.name()).isEqualTo("Portfolio");
        assertThat(portfolio.source()).isEqualTo(WealthSource.PORTFOLIO);
        assertThat(portfolio.auto()).isTrue();
        assertThat(portfolio.balance()).isEqualByComparingTo("3000");
        assertThat(portfolio.monthlyRate()).isNull();
        // no derivable deposit -> the forecast is just today's balance
        assertThat(portfolio.forecastYearEnd()).isEqualByComparingTo("3000");
        assertThat(portfolio.sharePct()).isEqualByComparingTo("37.50");

        WealthBucketOverviewResponse cash = overview.buckets().get(1);
        assertThat(cash.auto()).isFalse();
        assertThat(cash.id()).isNotEqualTo(WealthOverviewService.AUTO_PORTFOLIO_ID);
        assertThat(cash.sharePct()).isEqualByComparingTo("62.50");

        assertThat(overview.total()).isEqualByComparingTo("8000");
        then(portfolioService).should().getPortfolio(USER_ID);
        then(portfolioService).shouldHaveNoMoreInteractions();
    }

    @Test
    void no_portfolio_row_without_positions() {
        givenEmptyPortfolio();
        givenNoDefaultScenario();
        givenManualBuckets(manualBucket("Cash", "100", "0", 0));
        givenNoYtdBaseline();

        WealthOverviewResponse overview = overviewService.getOverview(USER_ID);

        assertThat(overview.buckets()).hasSize(1);
        assertThat(overview.buckets().getFirst().auto()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Auto pillar 3a row
    // -------------------------------------------------------------------------

    @Test
    void default_scenario_yields_an_auto_row_with_the_derived_monthly_rate() {
        givenEmptyPortfolio();
        givenDefaultScenario("25000", "7258");
        givenManualBuckets(manualBucket("Cash", "5000", "0", 0));
        givenNoYtdBaseline();

        WealthOverviewResponse overview = overviewService.getOverview(USER_ID);

        WealthBucketOverviewResponse pillar3 = overview.buckets().getFirst();
        assertThat(pillar3.id()).isEqualTo(WealthOverviewService.AUTO_PILLAR3_ID);
        assertThat(pillar3.name()).isEqualTo("Säule 3a");
        assertThat(pillar3.source()).isEqualTo(WealthSource.PILLAR3);
        assertThat(pillar3.auto()).isTrue();
        assertThat(pillar3.balance()).isEqualByComparingTo("25000");
        // 7258 / 12 = 604.8333… -> 604.83 (HALF_UP at scale 2)
        assertThat(pillar3.monthlyRate()).isEqualByComparingTo("604.83");
        assertThat(pillar3.forecastYearEnd()).isEqualByComparingTo(new BigDecimal("25000")
                .add(new BigDecimal("604.83").multiply(BigDecimal.valueOf(REMAINING_MONTHS))));

        assertThat(overview.total()).isEqualByComparingTo("30000");
        assertThat(overview.totalMonthlyRate()).isEqualByComparingTo("604.83");
    }

    @Test
    void zero_contribution_yields_a_null_monthly_rate() {
        givenEmptyPortfolio();
        givenDefaultScenario("25000", "0");
        givenManualBuckets();
        givenNoYtdBaseline();

        WealthOverviewResponse overview = overviewService.getOverview(USER_ID);

        WealthBucketOverviewResponse pillar3 = overview.buckets().getFirst();
        assertThat(pillar3.monthlyRate()).isNull();
        assertThat(pillar3.forecastYearEnd()).isEqualByComparingTo("25000");
        assertThat(overview.totalMonthlyRate()).isEqualByComparingTo("0");
    }

    @Test
    void no_pillar3_row_without_a_default_scenario() {
        givenEmptyPortfolio();
        givenNoDefaultScenario();
        givenManualBuckets(manualBucket("Cash", "100", "0", 0));
        givenNoYtdBaseline();

        WealthOverviewResponse overview = overviewService.getOverview(USER_ID);

        assertThat(overview.buckets()).hasSize(1);
        assertThat(overview.buckets().getFirst().source()).isEqualTo(WealthSource.MANUAL);
    }

    // -------------------------------------------------------------------------
    // Totals, forecast math & snapshot across auto and manual rows
    // -------------------------------------------------------------------------

    @Test
    void totals_and_snapshot_include_the_auto_rows() {
        givenPortfolio(position(AssetClass.ETF, "3000"));
        givenDefaultScenario("2000", "1200");
        givenManualBuckets(manualBucket("Cash", "5000", "500", 0));
        givenNoYtdBaseline();

        WealthOverviewResponse overview = overviewService.getOverview(USER_ID);

        assertThat(overview.buckets()).hasSize(3);
        assertThat(overview.buckets().get(0).id()).isEqualTo(WealthOverviewService.AUTO_PORTFOLIO_ID);
        assertThat(overview.buckets().get(1).id()).isEqualTo(WealthOverviewService.AUTO_PILLAR3_ID);
        assertThat(overview.buckets().get(2).auto()).isFalse();
        assertThat(overview.total()).isEqualByComparingTo("10000");
        // 1200 / 12 = 100 from the scenario plus 500 manual
        assertThat(overview.totalMonthlyRate()).isEqualByComparingTo("600");
        then(snapshotRepository).should().upsert(USER_ID, TODAY, new BigDecimal("10000.0000"));
    }

    @Test
    void months_remaining_counts_full_months_until_year_end() {
        assertThat(WealthOverviewService.monthsRemaining(LocalDate.of(2026, 1, 15))).isEqualTo(11);
        assertThat(WealthOverviewService.monthsRemaining(LocalDate.of(2026, 7, 11))).isEqualTo(5);
        assertThat(WealthOverviewService.monthsRemaining(LocalDate.of(2026, 12, 31))).isZero();
    }

    @Test
    void forecast_adds_the_monthly_rate_for_each_remaining_month() {
        givenEmptyPortfolio();
        givenNoDefaultScenario();
        givenManualBuckets(
                manualBucket("Cash", "5000", "500", 0),
                manualBucket("Household", "10000", "0", 1));
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

    @Test
    void zero_total_guards_the_share_computation() {
        givenEmptyPortfolio();
        givenNoDefaultScenario();
        givenManualBuckets(manualBucket("Cash", "0", "0", 0));
        givenNoYtdBaseline();

        WealthOverviewResponse overview = overviewService.getOverview(USER_ID);

        assertThat(overview.total()).isEqualByComparingTo("0");
        assertThat(overview.buckets().getFirst().sharePct()).isEqualByComparingTo("0");
    }

    // -------------------------------------------------------------------------
    // Year-to-date change
    // -------------------------------------------------------------------------

    @Test
    void ytd_change_is_computed_against_the_earliest_snapshot_of_the_year() {
        givenEmptyPortfolio();
        givenNoDefaultScenario();
        givenManualBuckets(manualBucket("Cash", "8000", "0", 0));
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
        givenEmptyPortfolio();
        givenNoDefaultScenario();
        givenManualBuckets(manualBucket("Cash", "8000", "0", 0));
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
        givenEmptyPortfolio();
        givenNoDefaultScenario();
        givenManualBuckets(manualBucket("Cash", "5000.5", "0", 0));
        givenNoYtdBaseline();

        overviewService.getOverview(USER_ID);

        then(snapshotRepository).should().upsert(USER_ID, TODAY, new BigDecimal("5000.5000"));
    }

    @Test
    void no_rows_at_all_yield_zero_totals_and_no_snapshot() {
        givenEmptyPortfolio();
        givenNoDefaultScenario();
        givenManualBuckets();

        WealthOverviewResponse overview = overviewService.getOverview(USER_ID);

        assertThat(overview.buckets()).isEmpty();
        assertThat(overview.total()).isEqualByComparingTo("0");
        assertThat(overview.totalMonthlyRate()).isEqualByComparingTo("0");
        assertThat(overview.totalForecastYearEnd()).isEqualByComparingTo("0");
        assertThat(overview.ytdChange()).isNull();
        assertThat(overview.ytdChangePct()).isNull();
        then(snapshotRepository).shouldHaveNoInteractions();
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
