package ch.finyo.investment;

import ch.finyo.common.SwissTime;
import ch.finyo.common.money.CurrencyCode;
import ch.finyo.fx.FxConverter;
import ch.finyo.fx.FxRate;
import ch.finyo.fx.FxRateRepository;
import ch.finyo.fx.FxRateType;
import ch.finyo.marketdata.MarketDataService;
import ch.finyo.marketdata.PricePoint;
import ch.finyo.marketdata.spi.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.anyCollection;
import static org.mockito.BDDMockito.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.lenient;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

/**
 * Unit tests for PortfolioService.
 *
 * Two properties dominate this class, and both are new in this PR:
 *
 * <ol>
 *   <li><b>The read path never touches the network.</b> Prices come from
 *       {@code instrument_price} in Postgres. Before, building a portfolio issued one
 *       synchronous SIX call per distinct instrument inside the user's request, so a vendor
 *       that accepted the connection and then went quiet pinned a Tomcat thread per position.
 *       {@code MarketDataService.refresh} is the only method that talks to a provider, and
 *       these tests assert it is never reached from a read — the assertion is what keeps the
 *       property true, because re-introducing the call would look like a helpful one-liner.</li>
 *   <li><b>A price always says where it came from.</b> The old code silently substituted the
 *       purchase price when SIX was unreachable: gain/loss read 0.00 and looked entirely
 *       plausible. MARKET / MANUAL / PURCHASE plus {@code priceAsOf} and {@code stale} are
 *       the fix, and the fallback order is a value judgement worth pinning down — a fact
 *       beats an opinion beats a receipt.</li>
 * </ol>
 *
 * And one thing this service no longer does: a GET does not write. The snapshot moved to
 * {@link PortfolioSnapshotJob}; here it must not happen at all.
 */
@DisplayName("PortfolioService")
@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    private static final String USER_ID = "user-pf-1";
    private static final String ISIN = "CH0038863350";
    private static final LocalDate TODAY = LocalDate.now(SwissTime.ZONE);

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private InstrumentFactsheetRepository factsheetRepository;

    @Mock
    private PortfolioSnapshotRepository snapshotRepository;

    @Mock
    private MarketDataService marketData;

    @Mock
    private FxRateRepository fxRateRepository;

    private PortfolioService portfolioService;

    @BeforeEach
    void wireService() {
        // A real FxConverter over a mocked rate repository, not a mocked converter: a CHF or
        // unknown-currency amount converts by identity without ever touching the repository, so
        // every CHF test here needs no stubbing at all — only the mixed-currency ones store a rate.
        FxConverter fxConverter = new FxConverter(fxRateRepository);
        portfolioService = new PortfolioService(positionRepository, instrumentRepository,
                factsheetRepository, snapshotRepository, marketData, fxConverter);
    }

    /** Makes the repository return a MID rate of chfPerUnit for a currency at or before any date. */
    private void givenRate(String currency, String chfPerUnit) {
        FxRate rate = FxRate.builder()
                .currency(currency)
                .rateDate(LocalDate.of(2026, 1, 1))
                .chfPerUnit(new BigDecimal(chfPerUnit))
                .rateType(FxRateType.MID)
                .source("frankfurter")
                .retrievedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        lenient().when(fxRateRepository
                        .findTopByCurrencyAndRateTypeAndRateDateLessThanEqualOrderByRateDateDesc(
                                eq(currency), eq(FxRateType.MID), any()))
                .thenReturn(Optional.of(rate));
    }

    // -------------------------------------------------------------------------
    // Builders & stubbing helpers
    // -------------------------------------------------------------------------

    private Instrument.InstrumentBuilder instrument(UUID id) {
        return Instrument.builder()
                .id(id)
                .userId(USER_ID)
                .name("Instrument " + id)
                .instrumentType(InstrumentType.STOCK)
                .currency(CurrencyCode.CHF)
                .sortOrder(0);
    }

    private Position position(UUID instrumentId, String quantity, String purchasePrice) {
        return Position.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .instrumentId(instrumentId)
                .quantity(new BigDecimal(quantity))
                .purchasePrice(new BigDecimal(purchasePrice))
                .purchaseDate(LocalDate.of(2026, 1, 15))
                .build();
    }

    private static PricePoint marketPrice(String price, LocalDate asOf, boolean stale) {
        return marketPrice(price, CurrencyCode.CHF, asOf, stale);
    }

    private static PricePoint marketPrice(String price, CurrencyCode currency, LocalDate asOf, boolean stale) {
        return new PricePoint(new BigDecimal(price), currency, asOf, DataSource.SIX, stale);
    }

    private void stubPortfolio(List<Position> positions, List<Instrument> instruments) {
        given(positionRepository.findByUserId(USER_ID)).willReturn(positions);
        given(instrumentRepository.findByIdInAndUserId(any(), eq(USER_ID))).willReturn(instruments);
    }

    private void stubNoMarketPrices() {
        given(marketData.latestPrices(anyCollection())).willReturn(Map.of());
    }

    // =========================================================================
    // The price chain: MARKET -> MANUAL -> PURCHASE
    // =========================================================================

    @Nested
    @DisplayName("prices a position: market, else the user's own, else what it cost")
    class PriceChain {

        @Test
        void takes_the_market_price_from_the_database_when_one_exists() {
            UUID instrumentId = UUID.randomUUID();
            stubPortfolio(
                    List.of(position(instrumentId, "10", "100.00")),
                    // A manual price exists too, and is deliberately different: a fact beats an opinion.
                    List.of(instrument(instrumentId).isin(ISIN).lastPrice(new BigDecimal("105.00")).build()));
            given(marketData.latestPrices(anyCollection()))
                    .willReturn(Map.of(ISIN, marketPrice("110.00", TODAY, false)));

            PortfolioResponse result = portfolioService.getPortfolio(USER_ID);

            PortfolioPositionResponse row = result.positions().getFirst();
            assertThat(row.priceSource()).isEqualTo(PriceSource.MARKET);
            assertThat(row.currentPrice()).isEqualByComparingTo("110.00");
            assertThat(row.priceAsOf()).isEqualTo(TODAY);
            assertThat(row.stale()).isFalse();
            assertThat(result.totalValue()).isEqualByComparingTo("1100.00");
        }

        @Test
        void falls_back_to_the_users_own_price_when_no_provider_prices_the_security() {
            // The normal case for an unlisted 3a fund: no provider quotes it, so the only
            // number anybody has is the one the user typed. It is labelled as theirs.
            UUID instrumentId = UUID.randomUUID();
            OffsetDateTime updatedAt = OffsetDateTime.of(2026, 7, 10, 8, 0, 0, 0, ZoneOffset.UTC);
            stubPortfolio(
                    List.of(position(instrumentId, "10", "100.00")),
                    List.of(instrument(instrumentId).isin(ISIN)
                            .lastPrice(new BigDecimal("105.00"))
                            .lastPriceUpdatedAt(updatedAt)
                            .build()));
            stubNoMarketPrices();

            PortfolioResponse result = portfolioService.getPortfolio(USER_ID);

            PortfolioPositionResponse row = result.positions().getFirst();
            assertThat(row.priceSource()).isEqualTo(PriceSource.MANUAL);
            assertThat(row.currentPrice()).isEqualByComparingTo("105.00");
            assertThat(row.priceAsOf()).isEqualTo(LocalDate.of(2026, 7, 10));
            assertThat(row.stale()).isFalse();
        }

        @Test
        void falls_back_to_the_purchase_price_and_says_so_when_nothing_else_is_known() {
            // The bug this PR exists to make visible. The old code substituted the purchase
            // price silently, so gain/loss was exactly 0.00 and the portfolio looked healthy.
            // The number is unchanged; what is new is that it admits what it is.
            UUID instrumentId = UUID.randomUUID();
            stubPortfolio(
                    List.of(position(instrumentId, "10", "100.00")),
                    List.of(instrument(instrumentId).build()));
            stubNoMarketPrices();

            PortfolioResponse result = portfolioService.getPortfolio(USER_ID);

            PortfolioPositionResponse row = result.positions().getFirst();
            assertThat(row.priceSource()).isEqualTo(PriceSource.PURCHASE);
            assertThat(row.currentPrice()).isEqualByComparingTo("100.00");
            assertThat(row.gainLoss()).isEqualByComparingTo("0");
            assertThat(row.priceAsOf()).isEqualTo(LocalDate.of(2026, 1, 15));
            assertThat(row.stale()).isFalse();
        }

        @Test
        void hands_the_stale_flag_straight_through_instead_of_hiding_an_old_price() {
            // An old price is not an error — an unlisted fund or a long weekend produces one
            // legitimately. It only becomes a lie if it is presented as today's.
            UUID instrumentId = UUID.randomUUID();
            LocalDate lastWeek = TODAY.minusDays(9);
            stubPortfolio(
                    List.of(position(instrumentId, "10", "100.00")),
                    List.of(instrument(instrumentId).isin(ISIN).build()));
            given(marketData.latestPrices(anyCollection()))
                    .willReturn(Map.of(ISIN, marketPrice("110.00", lastWeek, true)));

            PortfolioPositionResponse row = portfolioService.getPortfolio(USER_ID).positions().getFirst();

            assertThat(row.stale()).isTrue();
            assertThat(row.priceAsOf()).isEqualTo(lastWeek);
            // Still a market price, and still valued at it: stale is a label, not a reason to
            // fall back to the purchase price.
            assertThat(row.priceSource()).isEqualTo(PriceSource.MARKET);
            assertThat(row.currentPrice()).isEqualByComparingTo("110.00");
        }

        @Test
        void asks_the_database_for_each_distinct_isin_once_and_never_for_a_missing_one() {
            // Two positions in the same security are one row to look up, not two — and an
            // instrument without an ISIN cannot be priced at all, so it must not be asked for.
            UUID withIsin = UUID.randomUUID();
            UUID sameIsin = UUID.randomUUID();
            UUID withoutIsin = UUID.randomUUID();
            stubPortfolio(
                    List.of(position(withIsin, "1", "10.00"),
                            position(sameIsin, "1", "10.00"),
                            position(withoutIsin, "1", "10.00")),
                    List.of(instrument(withIsin).isin(ISIN).build(),
                            instrument(sameIsin).isin(ISIN).build(),
                            instrument(withoutIsin).build()));
            stubNoMarketPrices();

            portfolioService.getPortfolio(USER_ID);

            then(marketData).should().latestPrices(List.of(ISIN));
        }
    }

    // =========================================================================
    // The read path is offline
    // =========================================================================

    @Nested
    @DisplayName("a read never reaches for the network, and never writes")
    class ReadsAreReads {

        @Test
        void never_asks_a_provider_for_a_fresh_price_while_building_the_portfolio() {
            // The single most important assertion in this class. refresh() is the only method
            // that talks to SIX; calling it here would put an HTTP round trip per instrument
            // back inside the user's request — exactly the failure this PR removes.
            UUID instrumentId = UUID.randomUUID();
            stubPortfolio(
                    List.of(position(instrumentId, "10", "100.00")),
                    List.of(instrument(instrumentId).isin(ISIN).build()));
            stubNoMarketPrices();

            portfolioService.getPortfolio(USER_ID);

            then(marketData).should(never()).refresh(any());
        }

        @Test
        void never_asks_a_provider_while_building_a_position_detail_either() {
            UUID instrumentId = UUID.randomUUID();
            Position position = position(instrumentId, "10", "100.00");
            given(positionRepository.findByIdAndUserId(position.getId(), USER_ID))
                    .willReturn(Optional.of(position));
            stubPortfolio(List.of(position), List.of(instrument(instrumentId).isin(ISIN).build()));
            stubNoMarketPrices();
            given(factsheetRepository.findMetadataByInstrumentIdAndUserId(instrumentId, USER_ID))
                    .willReturn(Optional.empty());

            portfolioService.getPositionDetail(position.getId(), USER_ID);

            then(marketData).should(never()).refresh(any());
        }

        @Test
        void writes_no_snapshot_when_the_portfolio_is_merely_read() {
            // A GET used to mutate state, and the damage was quiet: the performance history
            // had a hole on every day the user did not log in, so the chart was really a
            // record of their visits. PortfolioSnapshotJob owns the write now.
            UUID instrumentId = UUID.randomUUID();
            stubPortfolio(
                    List.of(position(instrumentId, "10", "100.00")),
                    List.of(instrument(instrumentId).lastPrice(new BigDecimal("110.00")).build()));
            stubNoMarketPrices();

            portfolioService.getPortfolio(USER_ID);

            then(snapshotRepository).shouldHaveNoInteractions();
        }

        @Test
        void touches_nothing_at_all_for_an_empty_portfolio() {
            given(positionRepository.findByUserId(USER_ID)).willReturn(List.of());

            PortfolioResponse result = portfolioService.getPortfolio(USER_ID);

            assertThat(result.positions()).isEmpty();
            assertThat(result.totalValue()).isEqualByComparingTo("0");
            assertThat(result.returnPct()).isEqualByComparingTo("0");
            then(marketData).shouldHaveNoInteractions();
            then(snapshotRepository).shouldHaveNoInteractions();
        }
    }

    // =========================================================================
    // Currency
    // =========================================================================

    @Nested
    @DisplayName("currency")
    class Currency {

        @Test
        void reports_the_instruments_currency_for_a_purchase_price() {
            // No market and no manual price: valued at cost, and cost is in the instrument's
            // own currency. Nothing else could be right here — the number is the user's.
            UUID instrumentId = UUID.randomUUID();
            stubPortfolio(
                    List.of(position(instrumentId, "1", "10.00")),
                    List.of(instrument(instrumentId).currency(new CurrencyCode("USD")).build()));
            stubNoMarketPrices();

            assertThat(portfolioService.getPortfolio(USER_ID).positions().getFirst().currency())
                    .isEqualTo("USD");
        }

        @Test
        void reports_the_instruments_currency_for_a_manual_price() {
            // A manual price is a number the user typed against the instrument, so it is in the
            // instrument's currency — the same branch as purchase, kept honest by its own test.
            UUID instrumentId = UUID.randomUUID();
            stubPortfolio(
                    List.of(position(instrumentId, "1", "10.00")),
                    List.of(instrument(instrumentId)
                            .currency(new CurrencyCode("USD"))
                            .lastPrice(new BigDecimal("12.00"))
                            .build()));
            stubNoMarketPrices();

            PortfolioPositionResponse row = portfolioService.getPortfolio(USER_ID).positions().getFirst();
            assertThat(row.priceSource()).isEqualTo(PriceSource.MANUAL);
            assertThat(row.currency()).isEqualTo("USD");
        }

        @Test
        void reports_the_quotes_currency_for_a_market_price_not_the_instruments() {
            // The currentPrice — and therefore the value — comes from SIX in the quote's own
            // currency. The instrument's label may disagree (a USD-quoted fund carrying a stale
            // CHF label); the value is in USD, so USD is what the response must say. Labelling a
            // USD value as CHF is exactly the currency-mix-up this module exists to prevent, and
            // FX to a common currency is a later PR — until then it is shown as what it is.
            UUID instrumentId = UUID.randomUUID();
            stubPortfolio(
                    List.of(position(instrumentId, "1", "10.00")),
                    List.of(instrument(instrumentId).isin(ISIN).currency(CurrencyCode.CHF).build()));
            given(marketData.latestPrices(anyCollection()))
                    .willReturn(Map.of(ISIN, marketPrice("110.00", new CurrencyCode("USD"), TODAY, false)));

            PortfolioPositionResponse row = portfolioService.getPortfolio(USER_ID).positions().getFirst();
            assertThat(row.priceSource()).isEqualTo(PriceSource.MARKET);
            assertThat(row.currency()).isEqualTo("USD");
        }

        @Test
        void reports_the_quotes_currency_on_the_position_detail_too() {
            // The detail projection is built by a different method (toDetail) than the list row
            // (toResponse); the currency has to be threaded through both, so both are pinned.
            UUID instrumentId = UUID.randomUUID();
            Position position = position(instrumentId, "1", "10.00");
            given(positionRepository.findByIdAndUserId(position.getId(), USER_ID))
                    .willReturn(Optional.of(position));
            stubPortfolio(List.of(position),
                    List.of(instrument(instrumentId).isin(ISIN).currency(CurrencyCode.CHF).build()));
            given(marketData.latestPrices(anyCollection()))
                    .willReturn(Map.of(ISIN, marketPrice("110.00", new CurrencyCode("USD"), TODAY, false)));
            given(factsheetRepository.findMetadataByInstrumentIdAndUserId(instrumentId, USER_ID))
                    .willReturn(Optional.empty());

            PositionDetailResponse detail = portfolioService.getPositionDetail(position.getId(), USER_ID);

            assertThat(detail.priceSource()).isEqualTo(PriceSource.MARKET);
            assertThat(detail.currency()).isEqualTo("USD");
        }

        @Test
        void leaves_an_unknown_currency_null_rather_than_calling_it_francs() {
            // OpenFIGI publishes no currency, and an unlisted fund may never have been
            // resolved at all — so null is the ordinary outcome, not an edge case. CHF here
            // would hand the FX converter a guess dressed as a fact.
            UUID instrumentId = UUID.randomUUID();
            stubPortfolio(
                    List.of(position(instrumentId, "1", "10.00")),
                    List.of(instrument(instrumentId).currency(null).build()));
            stubNoMarketPrices();

            assertThat(portfolioService.getPortfolio(USER_ID).positions().getFirst().currency())
                    .isNull();
        }
    }

    // =========================================================================
    // Aggregation
    // =========================================================================

    @Nested
    @DisplayName("aggregation")
    class Aggregation {

        @Test
        void aggregates_totals_return_and_allocation_across_positions() {
            UUID firstId = UUID.randomUUID();
            UUID secondId = UUID.randomUUID();
            stubPortfolio(
                    List.of(
                            position(firstId, "10", "100.00"),   // value 1'100, cost 1'000
                            position(secondId, "5", "80.00")),   // value   550, cost   400
                    List.of(
                            instrument(firstId).lastPrice(new BigDecimal("110.00")).build(),
                            instrument(secondId).lastPrice(new BigDecimal("110.00")).build()));
            stubNoMarketPrices();

            PortfolioResponse result = portfolioService.getPortfolio(USER_ID);

            assertThat(result.totalValue()).isEqualByComparingTo("1650.00");
            assertThat(result.totalCost()).isEqualByComparingTo("1400.00");
            assertThat(result.gainLoss()).isEqualByComparingTo("250.00");
            assertThat(result.returnPct()).isEqualTo(new BigDecimal("17.86")); // 250/1400 HALF_UP

            PortfolioPositionResponse first = result.positions().getFirst();
            assertThat(first.allocationPct()).isEqualTo(new BigDecimal("66.67")); // 1100/1650
            assertThat(first.returnPct()).isEqualTo(new BigDecimal("10.00"));
            assertThat(result.positions().get(1).allocationPct()).isEqualTo(new BigDecimal("33.33"));
        }

        @Test
        void returns_zero_returnPct_when_the_cost_basis_is_zero() {
            UUID instrumentId = UUID.randomUUID();
            stubPortfolio(
                    List.of(position(instrumentId, "10", "0.00")),
                    List.of(instrument(instrumentId).lastPrice(new BigDecimal("5.00")).build()));
            stubNoMarketPrices();

            PortfolioResponse result = portfolioService.getPortfolio(USER_ID);

            assertThat(result.returnPct()).isEqualByComparingTo("0");
            assertThat(result.positions().getFirst().returnPct()).isEqualByComparingTo("0");
        }

        @Test
        void returns_zero_allocationPct_when_the_total_value_is_zero() {
            UUID instrumentId = UUID.randomUUID();
            stubPortfolio(
                    List.of(position(instrumentId, "10", "0.00")),
                    List.of(instrument(instrumentId).build()));
            stubNoMarketPrices();

            PortfolioResponse result = portfolioService.getPortfolio(USER_ID);

            assertThat(result.totalValue()).isEqualByComparingTo("0");
            assertThat(result.positions().getFirst().allocationPct()).isEqualByComparingTo("0");
        }
    }

    // =========================================================================
    // Foreign currency — the wrong number this PR exists to fix
    // =========================================================================

    @Nested
    @DisplayName("converts foreign currency to CHF at the aggregation boundary")
    class ForeignCurrency {

        @Test
        void converts_a_usd_position_to_chf_rather_than_summing_it_as_francs() {
            // The regression test for the bug. The value is shown in USD (its own currency) and
            // converted to CHF for the total, with the rate carried so the number can be checked.
            UUID instrumentId = UUID.randomUUID();
            givenRate("USD", "0.80");
            stubPortfolio(
                    List.of(position(instrumentId, "10", "100.00")),
                    List.of(instrument(instrumentId)
                            .currency(new CurrencyCode("USD"))
                            .lastPrice(new BigDecimal("120.00"))
                            .build()));
            stubNoMarketPrices();

            PortfolioResponse result = portfolioService.getPortfolio(USER_ID);
            PortfolioPositionResponse row = result.positions().getFirst();

            assertThat(row.currency()).isEqualTo("USD");
            assertThat(row.value()).isEqualByComparingTo("1200.00");     // native USD
            assertThat(row.valueChf()).isEqualByComparingTo("960.00");   // 1200 × 0.80
            assertThat(row.fxRate()).isEqualByComparingTo("0.80");
            assertThat(row.fxRateType()).isEqualTo(FxRateType.MID);
            assertThat(result.totalValue()).isEqualByComparingTo("960.00");
            assertThat(result.totalCurrency()).isEqualTo("CHF");
            assertThat(result.hasUnconverted()).isFalse();
        }

        @Test
        void sums_a_chf_and_a_usd_position_in_chf_not_by_face_value() {
            // 1000 CHF + 1000 USD is not 2000 of anything. At 0.80 the total is 1800 CHF — the
            // single assertion that would have caught the original bug.
            UUID chf = UUID.randomUUID();
            UUID usd = UUID.randomUUID();
            givenRate("USD", "0.80");
            stubPortfolio(
                    List.of(position(chf, "10", "100.00"), position(usd, "10", "100.00")),
                    List.of(
                            instrument(chf).lastPrice(new BigDecimal("100.00")).build(),          // 1000 CHF
                            instrument(usd).currency(new CurrencyCode("USD"))
                                    .lastPrice(new BigDecimal("100.00")).build()));                // 1000 USD → 800 CHF
            stubNoMarketPrices();

            PortfolioResponse result = portfolioService.getPortfolio(USER_ID);

            assertThat(result.totalValue()).isEqualByComparingTo("1800.00");
            assertThat(result.hasUnconverted()).isFalse();
        }

        @Test
        void excludes_a_position_whose_currency_has_no_rate_and_flags_the_total() {
            // No rate stored for GBP yet. The honest move is to keep it out of the CHF total and
            // say the total is partial — never to add its face value as though it were francs.
            UUID instrumentId = UUID.randomUUID();
            stubPortfolio(
                    List.of(position(instrumentId, "10", "100.00")),
                    List.of(instrument(instrumentId)
                            .currency(new CurrencyCode("GBP"))
                            .lastPrice(new BigDecimal("100.00"))
                            .build()));
            stubNoMarketPrices();

            PortfolioResponse result = portfolioService.getPortfolio(USER_ID);
            PortfolioPositionResponse row = result.positions().getFirst();

            assertThat(row.value()).isEqualByComparingTo("1000.00");   // native value still shown
            assertThat(row.valueChf()).isNull();
            assertThat(row.gainLoss()).isNull();
            assertThat(row.allocationPct()).isNull();
            assertThat(row.fxRate()).isNull();
            assertThat(result.totalValue()).isEqualByComparingTo("0");
            assertThat(result.hasUnconverted()).isTrue();
        }

        @Test
        void applies_no_rate_and_no_lookup_to_a_chf_position() {
            // The common case: a CHF position converts by identity, so no rate is attached and the
            // repository is never even consulted — the read stays a pure database read.
            UUID instrumentId = UUID.randomUUID();
            stubPortfolio(
                    List.of(position(instrumentId, "10", "100.00")),
                    List.of(instrument(instrumentId).lastPrice(new BigDecimal("110.00")).build()));
            stubNoMarketPrices();

            PortfolioPositionResponse row = portfolioService.getPortfolio(USER_ID).positions().getFirst();

            assertThat(row.currency()).isEqualTo("CHF");
            assertThat(row.valueChf()).isEqualByComparingTo("1100.00");
            assertThat(row.fxRate()).isNull();
            assertThat(row.fxRateDate()).isNull();
            then(fxRateRepository).shouldHaveNoInteractions();
        }
    }

    // =========================================================================
    // writeSnapshot() — what the nightly job calls
    // =========================================================================

    @Nested
    @DisplayName("writeSnapshot — the nightly job's entry point")
    class WriteSnapshot {

        @Test
        void upserts_todays_snapshot_with_the_aggregated_totals() {
            UUID instrumentId = UUID.randomUUID();
            stubPortfolio(
                    List.of(position(instrumentId, "10", "100.00")),
                    List.of(instrument(instrumentId).lastPrice(new BigDecimal("110.00")).build()));
            stubNoMarketPrices();

            portfolioService.writeSnapshot(USER_ID);

            then(snapshotRepository).should().upsert(
                    USER_ID,
                    TODAY,
                    new BigDecimal("1100.0000"),
                    new BigDecimal("1000.0000"));
        }

        @Test
        void writes_nothing_for_a_user_who_holds_nothing() {
            given(positionRepository.findByUserId(USER_ID)).willReturn(List.of());

            portfolioService.writeSnapshot(USER_ID);

            then(snapshotRepository).shouldHaveNoInteractions();
        }

        @Test
        void snapshots_the_market_value_rather_than_what_the_position_cost() {
            // The history is only worth having if it tracks the market. Snapshotting cost
            // would draw a flat line and call it performance.
            UUID instrumentId = UUID.randomUUID();
            stubPortfolio(
                    List.of(position(instrumentId, "10", "100.00")),
                    List.of(instrument(instrumentId).isin(ISIN).build()));
            given(marketData.latestPrices(anyCollection()))
                    .willReturn(Map.of(ISIN, marketPrice("130.00", TODAY, false)));

            portfolioService.writeSnapshot(USER_ID);

            then(snapshotRepository).should().upsert(
                    USER_ID, TODAY, new BigDecimal("1300.0000"), new BigDecimal("1000.0000"));
        }
    }

    // =========================================================================
    // getHistory()
    // =========================================================================

    @Test
    void getHistory_maps_snapshots_from_the_requested_months_back() {
        LocalDate expectedFrom = TODAY.minusMonths(6);
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
        PortfolioHistoryPoint point = result.points().getFirst();
        assertThat(point.date()).isEqualTo(expectedFrom.plusDays(1));
        assertThat(point.totalValue()).isEqualByComparingTo("1100");
        assertThat(point.totalCost()).isEqualByComparingTo("1000");
    }
}
