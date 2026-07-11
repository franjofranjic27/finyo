package ch.finyo.investment;

import ch.finyo.common.SwissTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.times;

/**
 * Pure unit tests for PortfolioService.
 *
 * Focus areas:
 *   1. Price fallback chain: SIX live -> persisted instrument price -> purchase price.
 *   2. Aggregation math incl. the zero guards (cost=0, totalValue=0).
 *   3. Snapshot upsert: insert vs. update, race retry, no snapshot for an
 *      empty portfolio.
 *   4. History window derived from months back.
 */
@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    private static final String USER_ID = "user-pf-1";

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private PortfolioSnapshotRepository snapshotRepository;

    @Mock
    private SixMarketDataClient sixClient;

    @InjectMocks
    private PortfolioService portfolioService;

    // -------------------------------------------------------------------------
    // Builders & stubbing helpers
    // -------------------------------------------------------------------------

    private Instrument instrument(UUID id, String valor, BigDecimal lastPrice) {
        return Instrument.builder()
                .id(id)
                .userId(USER_ID)
                .valor(valor)
                .name("Instrument " + id)
                .instrumentType(InstrumentType.STOCK)
                .sortOrder(0)
                .lastPrice(lastPrice)
                .build();
    }

    private Position position(UUID instrumentId, String quantity, String purchasePrice) {
        return Position.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .instrumentId(instrumentId)
                .quantity(new BigDecimal(quantity))
                .purchasePrice(new BigDecimal(purchasePrice))
                .build();
    }

    private MarketDataResponse liveData(BigDecimal lastPrice) {
        return new MarketDataResponse(
                "3886335", "CH0038863350", "NESN", "Nestlé", "SIX", "CHF", lastPrice,
                null, null, null, null, null, null,
                null, null, null, null, "STOCK", "Consumer", null,
                OffsetDateTime.now(ZoneOffset.UTC), false);
    }

    private void stubPortfolio(List<Position> positions, List<Instrument> instruments) {
        given(positionRepository.findByUserId(USER_ID)).willReturn(positions);
        given(instrumentRepository.findAllById(any())).willReturn(instruments);
    }

    // =========================================================================
    // getPortfolio() — price fallback chain
    // =========================================================================

    @Test
    void getPortfolio_uses_the_live_six_price_when_available() {
        UUID instrumentId = UUID.randomUUID();
        stubPortfolio(
                List.of(position(instrumentId, "10", "100.00")),
                List.of(instrument(instrumentId, "3886335", new BigDecimal("105.00"))));
        given(sixClient.fetchByValorOrIsin("3886335"))
                .willReturn(Optional.of(liveData(new BigDecimal("110.00"))));

        PortfolioResponse result = portfolioService.getPortfolio(USER_ID);

        PortfolioPositionResponse row = result.positions().get(0);
        assertThat(row.priceSource()).isEqualTo(PriceSource.LIVE);
        assertThat(row.currentPrice()).isEqualByComparingTo("110.00");
        assertThat(result.totalValue()).isEqualByComparingTo("1100.00");
    }

    @Test
    void getPortfolio_falls_back_to_the_persisted_price_when_six_is_unavailable() {
        UUID instrumentId = UUID.randomUUID();
        stubPortfolio(
                List.of(position(instrumentId, "10", "100.00")),
                List.of(instrument(instrumentId, "3886335", new BigDecimal("105.00"))));
        given(sixClient.fetchByValorOrIsin("3886335")).willReturn(Optional.empty());

        PortfolioResponse result = portfolioService.getPortfolio(USER_ID);

        PortfolioPositionResponse row = result.positions().get(0);
        assertThat(row.priceSource()).isEqualTo(PriceSource.CACHE);
        assertThat(row.currentPrice()).isEqualByComparingTo("105.00");
    }

    @Test
    void getPortfolio_falls_back_to_the_purchase_price_when_no_price_is_known_at_all() {
        UUID instrumentId = UUID.randomUUID();
        stubPortfolio(
                List.of(position(instrumentId, "10", "100.00")),
                List.of(instrument(instrumentId, null, null)));

        PortfolioResponse result = portfolioService.getPortfolio(USER_ID);

        PortfolioPositionResponse row = result.positions().get(0);
        assertThat(row.priceSource()).isEqualTo(PriceSource.PURCHASE);
        assertThat(row.gainLoss()).isEqualByComparingTo("0");
        then(sixClient).shouldHaveNoInteractions();
    }

    @Test
    void getPortfolio_fetches_each_distinct_identifier_only_once() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        stubPortfolio(
                List.of(position(firstId, "1", "10.00"), position(secondId, "1", "20.00")),
                List.of(instrument(firstId, "3886335", null), instrument(secondId, "3886335", null)));
        given(sixClient.fetchByValorOrIsin("3886335"))
                .willReturn(Optional.of(liveData(new BigDecimal("30.00"))));

        portfolioService.getPortfolio(USER_ID);

        then(sixClient).should(times(1)).fetchByValorOrIsin("3886335");
    }

    // =========================================================================
    // getPortfolio() — aggregation
    // =========================================================================

    @Test
    void getPortfolio_aggregates_totals_return_and_allocation_across_positions() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        stubPortfolio(
                List.of(
                        position(firstId, "10", "100.00"),   // value 1'100, cost 1'000
                        position(secondId, "5", "80.00")),   // value 550, cost 400
                List.of(
                        instrument(firstId, null, new BigDecimal("110.00")),
                        instrument(secondId, null, new BigDecimal("110.00"))));

        PortfolioResponse result = portfolioService.getPortfolio(USER_ID);

        assertThat(result.totalValue()).isEqualByComparingTo("1650.00");
        assertThat(result.totalCost()).isEqualByComparingTo("1400.00");
        assertThat(result.gainLoss()).isEqualByComparingTo("250.00");
        assertThat(result.returnPct()).isEqualTo(new BigDecimal("17.86")); // 250/1400 HALF_UP

        PortfolioPositionResponse first = result.positions().get(0);
        assertThat(first.allocationPct()).isEqualTo(new BigDecimal("66.67")); // 1100/1650
        assertThat(first.returnPct()).isEqualTo(new BigDecimal("10.00"));
        assertThat(result.positions().get(1).allocationPct()).isEqualTo(new BigDecimal("33.33"));
    }

    @Test
    void getPortfolio_returns_zero_returnPct_when_the_cost_basis_is_zero() {
        UUID instrumentId = UUID.randomUUID();
        stubPortfolio(
                List.of(position(instrumentId, "10", "0.00")),
                List.of(instrument(instrumentId, null, new BigDecimal("5.00"))));

        PortfolioResponse result = portfolioService.getPortfolio(USER_ID);

        assertThat(result.returnPct()).isEqualByComparingTo("0");
        assertThat(result.positions().get(0).returnPct()).isEqualByComparingTo("0");
    }

    @Test
    void getPortfolio_returns_zero_allocationPct_when_the_total_value_is_zero() {
        UUID instrumentId = UUID.randomUUID();
        stubPortfolio(
                List.of(position(instrumentId, "10", "0.00")),
                List.of(instrument(instrumentId, null, null)));

        PortfolioResponse result = portfolioService.getPortfolio(USER_ID);

        assertThat(result.totalValue()).isEqualByComparingTo("0");
        assertThat(result.positions().get(0).allocationPct()).isEqualByComparingTo("0");
    }

    // =========================================================================
    // getPortfolio() — snapshot upsert
    // =========================================================================

    @Test
    void getPortfolio_inserts_a_snapshot_when_none_exists_for_today() {
        UUID instrumentId = UUID.randomUUID();
        stubPortfolio(
                List.of(position(instrumentId, "10", "100.00")),
                List.of(instrument(instrumentId, null, new BigDecimal("110.00"))));
        given(snapshotRepository.findByUserIdAndSnapshotDate(USER_ID, LocalDate.now(SwissTime.ZONE)))
                .willReturn(Optional.empty());

        portfolioService.getPortfolio(USER_ID);

        then(snapshotRepository).should().saveAndFlush(argThat(s ->
                USER_ID.equals(s.getUserId())
                        && LocalDate.now(SwissTime.ZONE).equals(s.getSnapshotDate())
                        && new BigDecimal("1100.0000").equals(s.getTotalValue())
                        && new BigDecimal("1000.0000").equals(s.getTotalCost())));
        then(snapshotRepository).should(never()).save(any());
    }

    @Test
    void getPortfolio_updates_todays_snapshot_when_it_already_exists() {
        UUID instrumentId = UUID.randomUUID();
        stubPortfolio(
                List.of(position(instrumentId, "10", "100.00")),
                List.of(instrument(instrumentId, null, new BigDecimal("110.00"))));
        UUID snapshotId = UUID.randomUUID();
        given(snapshotRepository.findByUserIdAndSnapshotDate(USER_ID, LocalDate.now(SwissTime.ZONE)))
                .willReturn(Optional.of(PortfolioSnapshot.builder()
                        .id(snapshotId)
                        .userId(USER_ID)
                        .snapshotDate(LocalDate.now(SwissTime.ZONE))
                        .totalValue(new BigDecimal("999.0000"))
                        .totalCost(new BigDecimal("900.0000"))
                        .build()));

        portfolioService.getPortfolio(USER_ID);

        then(snapshotRepository).should().save(argThat(s ->
                snapshotId.equals(s.getId())
                        && new BigDecimal("1100.0000").equals(s.getTotalValue())));
        then(snapshotRepository).should(never()).saveAndFlush(any());
    }

    @Test
    void getPortfolio_retries_the_snapshot_as_update_when_a_concurrent_insert_wins() {
        UUID instrumentId = UUID.randomUUID();
        stubPortfolio(
                List.of(position(instrumentId, "10", "100.00")),
                List.of(instrument(instrumentId, null, new BigDecimal("110.00"))));
        UUID snapshotId = UUID.randomUUID();
        PortfolioSnapshot concurrentlyInserted = PortfolioSnapshot.builder()
                .id(snapshotId)
                .userId(USER_ID)
                .snapshotDate(LocalDate.now(SwissTime.ZONE))
                .totalValue(new BigDecimal("1.0000"))
                .totalCost(new BigDecimal("1.0000"))
                .build();
        given(snapshotRepository.findByUserIdAndSnapshotDate(USER_ID, LocalDate.now(SwissTime.ZONE)))
                .willReturn(Optional.empty(), Optional.of(concurrentlyInserted));
        given(snapshotRepository.saveAndFlush(any(PortfolioSnapshot.class)))
                .willThrow(new DataIntegrityViolationException("uq_portfolio_snapshot_user_date"));

        portfolioService.getPortfolio(USER_ID);

        then(snapshotRepository).should().save(argThat(s ->
                snapshotId.equals(s.getId())
                        && new BigDecimal("1100.0000").equals(s.getTotalValue())));
    }

    @Test
    void getPortfolio_writes_no_snapshot_for_an_empty_portfolio() {
        given(positionRepository.findByUserId(USER_ID)).willReturn(List.of());

        PortfolioResponse result = portfolioService.getPortfolio(USER_ID);

        assertThat(result.positions()).isEmpty();
        assertThat(result.totalValue()).isEqualByComparingTo("0");
        assertThat(result.returnPct()).isEqualByComparingTo("0");
        then(snapshotRepository).shouldHaveNoInteractions();
        then(sixClient).shouldHaveNoInteractions();
    }

    // =========================================================================
    // getHistory()
    // =========================================================================

    @Test
    void getHistory_maps_snapshots_from_the_requested_months_back() {
        LocalDate expectedFrom = LocalDate.now(SwissTime.ZONE).minusMonths(6);
        PortfolioSnapshot snapshot = PortfolioSnapshot.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .snapshotDate(expectedFrom.plusDays(1))
                .totalValue(new BigDecimal("1100.0000"))
                .totalCost(new BigDecimal("1000.0000"))
                .build();
        given(snapshotRepository.findByUserIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(
                USER_ID, expectedFrom))
                .willReturn(List.of(snapshot));

        PortfolioHistoryResponse result = portfolioService.getHistory(USER_ID, 6);

        assertThat(result.points()).hasSize(1);
        PortfolioHistoryPoint point = result.points().get(0);
        assertThat(point.date()).isEqualTo(expectedFrom.plusDays(1));
        assertThat(point.totalValue()).isEqualByComparingTo("1100");
        assertThat(point.totalCost()).isEqualByComparingTo("1000");
    }
}
