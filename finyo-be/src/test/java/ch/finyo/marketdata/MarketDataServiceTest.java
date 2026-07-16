package ch.finyo.marketdata;

import ch.finyo.common.SourceResult;
import ch.finyo.common.SwissTime;
import ch.finyo.common.money.CurrencyCode;
import ch.finyo.marketdata.spi.DataSource;
import ch.finyo.marketdata.spi.PriceBar;
import ch.finyo.marketdata.spi.PriceHistoryProvider;
import ch.finyo.marketdata.spi.Quote;
import ch.finyo.marketdata.spi.QuoteProvider;
import ch.finyo.marketdata.spi.SecurityId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

/**
 * Unit tests for MarketDataService — the application's only source of prices.
 *
 * Three properties, each of which used to be violated and each of which failed quietly:
 *
 * <ol>
 *   <li><b>Reads never touch the network.</b> {@code latestPrices} answers from Postgres or
 *       not at all. A single provider call on this path puts a synchronous HTTP round trip
 *       back inside the user's request.</li>
 *   <li><b>An outage writes nothing.</b> {@code Unavailable} says something about the vendor
 *       and nothing about the security, so a failed refresh must leave the last good price
 *       exactly where it is. Storing anything here would let a five-minute SIX blip overwrite
 *       a real close with a guess — and the guess would then look like a fact forever.</li>
 *   <li><b>An old price says it is old.</b> {@code stale} is what stops a three-week-old
 *       number being shown as today's.</li>
 * </ol>
 *
 * The providers are mocks: whether SIX parses its own payload is SixQuoteAdapterTest's
 * subject, not this one's.
 */
@DisplayName("MarketDataService")
@ExtendWith(MockitoExtension.class)
class MarketDataServiceTest {

    private static final String ISIN = "CH0038863350";
    private static final String OTHER_ISIN = "IE00B4L5Y983";
    private static final LocalDate TODAY = LocalDate.now(SwissTime.ZONE);
    private static final OffsetDateTime RETRIEVED_AT =
            OffsetDateTime.of(2026, 7, 14, 22, 30, 0, 0, ZoneOffset.UTC);

    @Mock
    private QuoteProvider six;

    @Mock
    private QuoteProvider eodhd;

    @Mock
    private PriceHistoryProvider sixHistory;

    @Mock
    private InstrumentPriceRepository repository;

    @Mock
    private InstrumentPriceWriter writer;

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    /**
     * The bean list is deliberately [six, eodhd] everywhere, so a test that configures the
     * reverse order proves configuration wins rather than luck. No history provider — the
     * read/refresh tests never touch that chain.
     */
    private MarketDataService serviceWithChain(String... quoteProviders) {
        return service(List.of(quoteProviders), List.of(), List.of());
    }

    /** A service wired with the history chain too, for the backfill and job-entry tests. */
    private MarketDataService serviceWithHistory(List<String> quoteChain, List<String> historyChain) {
        return service(quoteChain, historyChain, List.of(sixHistory));
    }

    private MarketDataService service(List<String> quoteChain, List<String> historyChain,
                                      List<PriceHistoryProvider> historyBeans) {
        MarketDataProperties properties = new MarketDataProperties(
                List.of(),
                quoteChain,
                historyChain,
                new MarketDataProperties.SixProperties(true, "http://six.invalid"),
                new MarketDataProperties.OpenFigiProperties(false, null, null),
                new MarketDataProperties.EodhdProperties(false, null, null));

        return new MarketDataService(List.of(six, eodhd), historyBeans, properties, repository, writer);
    }

    private static InstrumentPrice storedPrice(String isin, String close, LocalDate priceDate) {
        return InstrumentPrice.builder()
                .isin(isin)
                .priceDate(priceDate)
                .close(new BigDecimal(close))
                .currency(CurrencyCode.CHF)
                .source(DataSource.SIX)
                .retrievedAt(RETRIEVED_AT)
                .build();
    }

    private static Quote quote(String isin, String price) {
        return new Quote(isin, new BigDecimal(price), CurrencyCode.CHF, TODAY,
                RETRIEVED_AT, true, DataSource.SIX);
    }

    private static Quote quote(String isin, String price, CurrencyCode currency) {
        return new Quote(isin, new BigDecimal(price), currency, TODAY,
                RETRIEVED_AT, true, DataSource.SIX);
    }

    private static PriceBar bar(LocalDate date, String close) {
        return new PriceBar(date, new BigDecimal(close));
    }

    /** The bar-as-stored quote the backfill builds: the current quote's currency, source and
     * metadata carried onto the bar's date and close. */
    private static Quote storedBar(String isin, String close, CurrencyCode currency, LocalDate date) {
        return new Quote(isin, new BigDecimal(close), currency, date,
                RETRIEVED_AT, true, DataSource.SIX);
    }

    private void nameTheProviders() {
        given(six.name()).willReturn("six");
        given(eodhd.name()).willReturn("eodhd");
    }

    private void nameTheHistoryProvider() {
        given(sixHistory.name()).willReturn("six");
    }

    // =========================================================================
    // latestPrices / latestPrice — database only
    // =========================================================================

    @Nested
    @DisplayName("reads answer from the database, and only from the database")
    class ReadsAreOffline {

        @Test
        void reads_the_latest_price_of_every_held_isin_in_one_query() {
            nameTheProviders();
            given(repository.findLatestForEach(List.of(ISIN, OTHER_ISIN))).willReturn(List.of(
                    storedPrice(ISIN, "83.88", TODAY),
                    storedPrice(OTHER_ISIN, "144.20", TODAY)));

            Map<String, PricePoint> prices = serviceWithChain("six").latestPrices(List.of(ISIN, OTHER_ISIN));

            assertThat(prices).hasSize(2);
            assertThat(prices.get(ISIN).price()).isEqualByComparingTo("83.88");
            assertThat(prices.get(ISIN).currency()).isEqualTo(CurrencyCode.CHF);
            assertThat(prices.get(ISIN).asOf()).isEqualTo(TODAY);
            assertThat(prices.get(ISIN).source()).isEqualTo(DataSource.SIX);
        }

        @Test
        void never_asks_a_provider_for_a_price_it_does_not_have_in_the_database() {
            // The whole point of the class. A miss is a miss — it is not an invitation to
            // fetch, because fetching here would be an HTTP call inside a user's request.
            nameTheProviders();
            given(repository.findLatestForEach(anyCollection())).willReturn(List.of());

            Map<String, PricePoint> prices = serviceWithChain("six").latestPrices(List.of(ISIN));

            assertThat(prices).isEmpty();
            then(six).should(never()).quote(any());
        }

        @Test
        void does_not_even_query_the_database_for_an_empty_portfolio() {
            nameTheProviders();

            assertThat(serviceWithChain("six").latestPrices(List.of())).isEmpty();

            then(repository).shouldHaveNoInteractions();
        }

        @Test
        void latestPrice_returns_the_newest_row_for_one_security() {
            nameTheProviders();
            given(repository.findFirstByIsinOrderByPriceDateDesc(ISIN))
                    .willReturn(Optional.of(storedPrice(ISIN, "83.88", TODAY)));

            Optional<PricePoint> price = serviceWithChain("six").latestPrice(ISIN);

            assertThat(price).isPresent();
            assertThat(price.orElseThrow().price()).isEqualByComparingTo("83.88");
            then(six).should(never()).quote(any());
        }

        @Test
        void latestPrice_is_empty_for_a_security_nobody_ever_priced() {
            nameTheProviders();
            given(repository.findFirstByIsinOrderByPriceDateDesc(ISIN)).willReturn(Optional.empty());

            assertThat(serviceWithChain("six").latestPrice(ISIN)).isEmpty();
        }
    }

    // =========================================================================
    // stale
    // =========================================================================

    @Nested
    @DisplayName("stale: how old a market price is allowed to get before it is flagged")
    class Staleness {

        @ParameterizedTest(name = "a price from {0} day(s) ago is stale: {1}")
        @CsvSource({
                // A Friday close read on a Monday morning is three days behind and perfectly
                // normal — flagging it would make "stale" mean nothing.
                "0, false",
                "3, false",
                // Easter: a Thursday close read the following Tuesday. Four days is still the
                // market being shut, not the data being wrong. The boundary is inclusive.
                "4, false",
                // Beyond that nothing legitimate explains it: either the sync has been failing
                // or nobody quotes this security. Both are worth telling the user about.
                "5, true",
                "30, true"
        })
        void flags_a_price_only_once_no_closed_market_can_explain_its_age(int daysAgo, boolean expectedStale) {
            nameTheProviders();
            given(repository.findLatestForEach(anyCollection()))
                    .willReturn(List.of(storedPrice(ISIN, "83.88", TODAY.minusDays(daysAgo))));

            Map<String, PricePoint> prices = serviceWithChain("six").latestPrices(List.of(ISIN));

            assertThat(prices.get(ISIN).stale()).isEqualTo(expectedStale);
        }

        @Test
        void a_stale_price_is_still_returned_rather_than_withheld() {
            // An honest old number beats no number, and beats the fresh-looking wrong one the
            // old code produced by silently substituting the purchase price.
            nameTheProviders();
            given(repository.findLatestForEach(anyCollection()))
                    .willReturn(List.of(storedPrice(ISIN, "83.88", TODAY.minusDays(40))));

            PricePoint price = serviceWithChain("six").latestPrices(List.of(ISIN)).get(ISIN);

            assertThat(price.price()).isEqualByComparingTo("83.88");
            assertThat(price.stale()).isTrue();
        }
    }

    // =========================================================================
    // refresh — the only method that talks to a provider
    // =========================================================================

    @Nested
    @DisplayName("refresh: what may be written down, and what may not")
    class Refresh {

        @Test
        void stores_a_quote_the_provider_actually_answered_with() {
            nameTheProviders();
            given(six.supports(any())).willReturn(true);
            given(six.quote(new SecurityId.Isin(ISIN))).willReturn(SourceResult.found(quote(ISIN, "83.88")));

            int stored = serviceWithChain("six").refresh(List.of(ISIN));

            assertThat(stored).isEqualTo(1);
            then(writer).should().store(quote(ISIN, "83.88"));
        }

        @Test
        void writes_absolutely_nothing_when_the_provider_could_not_be_reached() {
            // The critical one. Unavailable says nothing about the security — only about SIX.
            // Anything written here would overwrite a perfectly good close with a fiction, and
            // the fiction would be indistinguishable from a real price the next morning.
            nameTheProviders();
            given(six.supports(any())).willReturn(true);
            given(six.quote(any())).willReturn(SourceResult.unavailable("six: read timed out"));

            int stored = serviceWithChain("six").refresh(List.of(ISIN));

            assertThat(stored).isZero();
            then(writer).shouldHaveNoInteractions();
        }

        @Test
        void writes_nothing_and_raises_nothing_when_no_provider_prices_the_security() {
            // The ordinary outcome for an unlisted 3a fund: nobody quotes it, and that is a
            // fact about the world, not a failure. The nightly job must not go red over it.
            nameTheProviders();
            given(six.supports(any())).willReturn(true);
            given(six.quote(any())).willReturn(SourceResult.notFound());

            int stored = serviceWithChain("six").refresh(List.of(ISIN));

            assertThat(stored).isZero();
            then(writer).shouldHaveNoInteractions();
        }

        @Test
        void a_provider_that_blows_up_is_an_outage_and_still_writes_nothing() {
            // An adapter throwing is a vendor problem wearing a stack trace. It must not take
            // the whole sync down, and it certainly must not be mistaken for a price.
            nameTheProviders();
            given(six.supports(any())).willReturn(true);
            given(six.quote(any())).willThrow(new IllegalStateException("FQS changed its payload"));

            int stored = serviceWithChain("six").refresh(List.of(ISIN));

            assertThat(stored).isZero();
            then(writer).shouldHaveNoInteractions();
        }

        @Test
        void one_unreachable_security_does_not_cost_the_others_their_price() {
            // The nightly job prices every held security in one pass. If the first failure
            // aborted the loop, one bad ISIN would silently freeze the whole portfolio.
            nameTheProviders();
            given(six.supports(any())).willReturn(true);
            given(six.quote(new SecurityId.Isin(ISIN)))
                    .willReturn(SourceResult.unavailable("six: read timed out"));
            given(six.quote(new SecurityId.Isin(OTHER_ISIN)))
                    .willReturn(SourceResult.found(quote(OTHER_ISIN, "144.20")));

            int stored = serviceWithChain("six").refresh(List.of(ISIN, OTHER_ISIN));

            assertThat(stored).isEqualTo(1);
            then(writer).should().store(quote(OTHER_ISIN, "144.20"));
        }

        @Test
        void skips_a_malformed_isin_instead_of_spending_a_request_on_it() {
            // The bulk-import path bypasses bean validation, so a CSV cell holding junk can
            // reach here. It is not worth a round trip to a source we are only tolerated on.
            nameTheProviders();

            int stored = serviceWithChain("six").refresh(List.of("not-an-isin"));

            assertThat(stored).isZero();
            then(six).should(never()).quote(any());
            then(writer).shouldHaveNoInteractions();
        }

        @Test
        void falls_through_to_the_next_provider_when_the_first_one_is_unreachable() {
            nameTheProviders();
            given(six.supports(any())).willReturn(true);
            given(eodhd.supports(any())).willReturn(true);
            given(six.quote(any())).willReturn(SourceResult.unavailable("six: 503"));
            given(eodhd.quote(any())).willReturn(SourceResult.found(quote(ISIN, "83.88")));

            int stored = serviceWithChain("six", "eodhd").refresh(List.of(ISIN));

            assertThat(stored).isEqualTo(1);
            then(writer).should().store(quote(ISIN, "83.88"));
        }

        @Test
        void stops_at_the_first_provider_that_has_a_price() {
            nameTheProviders();
            given(six.supports(any())).willReturn(true);
            given(six.quote(any())).willReturn(SourceResult.found(quote(ISIN, "83.88")));

            serviceWithChain("six", "eodhd").refresh(List.of(ISIN));

            then(eodhd).should(never()).quote(any());
        }

        @Test
        void skips_a_provider_that_cannot_handle_this_kind_of_identifier() {
            nameTheProviders();
            given(six.supports(any())).willReturn(false);

            int stored = serviceWithChain("six").refresh(List.of(ISIN));

            assertThat(stored).isZero();
            then(six).should(never()).quote(any());
        }

        @Test
        void ignores_a_provider_bean_that_is_not_in_the_configured_quote_chain() {
            // OpenFIGI is a bean and a provider, but it is symbology and has no prices at all.
            // Configuration, not bean presence, decides who is asked — which is what makes
            // swapping a vendor a YAML change on the day SIX's licence stops holding.
            nameTheProviders();
            given(eodhd.supports(any())).willReturn(true);
            given(eodhd.quote(any())).willReturn(SourceResult.found(quote(ISIN, "83.88")));

            int stored = serviceWithChain("eodhd").refresh(List.of(ISIN));

            assertThat(stored).isEqualTo(1);
            then(six).should(never()).supports(any());
            then(six).should(never()).quote(any());
        }

        @Test
        void asks_the_providers_in_the_configured_order_not_in_bean_order() {
            // The beans arrive as [six, eodhd]; the configuration says the opposite, and the
            // configuration wins — otherwise the fallback order is an accident of scanning.
            nameTheProviders();
            given(eodhd.supports(any())).willReturn(true);
            given(eodhd.quote(any())).willReturn(SourceResult.found(quote(ISIN, "83.88")));

            serviceWithChain("eodhd", "six").refresh(List.of(ISIN));

            then(eodhd).should().quote(any());
            then(six).should(never()).quote(any());
        }

        @Test
        void an_empty_chain_asks_nobody_and_fails_nothing() {
            // The state the test profile runs in, and the state production lands in if every
            // provider is switched off. It must degrade, not explode.
            nameTheProviders();

            assertThat(serviceWithChain().refresh(List.of(ISIN))).isZero();

            then(writer).shouldHaveNoInteractions();
        }
    }

    // =========================================================================
    // priceHistory — the stored time series, database only
    // =========================================================================

    @Nested
    @DisplayName("priceHistory: the stored daily closes, read from the database and only there")
    class PriceHistoryReads {

        private static final LocalDate FROM = TODAY.minusYears(3);

        @Test
        void returns_the_stored_closes_in_the_order_the_repository_yields_them() {
            // The chart draws them left to right, so the oldest-first ordering the query
            // guarantees has to survive the mapping untouched.
            nameTheProviders();
            given(repository.findByIsinAndPriceDateGreaterThanEqualOrderByPriceDateAsc(ISIN, FROM))
                    .willReturn(List.of(
                            storedPrice(ISIN, "80.00", TODAY.minusDays(2)),
                            storedPrice(ISIN, "82.50", TODAY.minusDays(1)),
                            storedPrice(ISIN, "83.88", TODAY)));

            List<PricePoint> history = serviceWithChain("six").priceHistory(ISIN, FROM);

            assertThat(history).extracting(PricePoint::asOf)
                    .containsExactly(TODAY.minusDays(2), TODAY.minusDays(1), TODAY);
            assertThat(history).extracting(point -> point.price().stripTrailingZeros().toPlainString())
                    .containsExactly("80", "82.5", "83.88");
        }

        @Test
        void never_asks_a_provider_for_a_history_it_does_not_have_in_the_database() {
            // The same guarantee reads carry everywhere in this class: a chart request is a
            // database read, never a synchronous HTTP round trip inside the user's request.
            nameTheProviders();
            given(repository.findByIsinAndPriceDateGreaterThanEqualOrderByPriceDateAsc(ISIN, FROM))
                    .willReturn(List.of());

            List<PricePoint> history = serviceWithChain("six").priceHistory(ISIN, FROM);

            assertThat(history).isEmpty();
            then(six).should(never()).quote(any());
            then(sixHistory).should(never()).history(any(), any());
        }
    }

    // =========================================================================
    // backfill — the only method that fetches a security's history
    // =========================================================================

    @Nested
    @DisplayName("backfill: fill in a security's history, stamping every bar with the quote's currency")
    class Backfill {

        @Test
        void stamps_every_bar_with_the_currency_of_the_current_quote_not_the_history_feed() {
            // The load-bearing case. charts.json carries dates and closes but no currency, so
            // the currency has to come from the current quote and be applied to every bar. A
            // USD ETF whose bars silently became CHF is the original bug rebuilt one level down.
            nameTheProviders();
            nameTheHistoryProvider();
            CurrencyCode usd = new CurrencyCode("USD");
            given(six.supports(any())).willReturn(true);
            given(six.quote(new SecurityId.Isin(OTHER_ISIN)))
                    .willReturn(SourceResult.found(quote(OTHER_ISIN, "144.20", usd)));
            given(sixHistory.supports(any())).willReturn(true);
            given(sixHistory.history(any(), any())).willReturn(SourceResult.found(List.of(
                    bar(TODAY.minusDays(2), "140.00"),
                    bar(TODAY.minusDays(1), "142.00"))));

            int stored = serviceWithHistory(List.of("six"), List.of("six")).backfill(OTHER_ISIN);

            // The current quote plus one row per bar.
            assertThat(stored).isEqualTo(3);
            then(writer).should().store(quote(OTHER_ISIN, "144.20", usd));
            then(writer).should().store(storedBar(OTHER_ISIN, "140.00", usd, TODAY.minusDays(2)));
            then(writer).should().store(storedBar(OTHER_ISIN, "142.00", usd, TODAY.minusDays(1)));
        }

        @Test
        void asks_for_history_going_back_three_years() {
            // Three years is the chart window, matched by the detail read. Asking for less would
            // leave the far end of the chart blank; asking for more would bloat the table.
            nameTheProviders();
            nameTheHistoryProvider();
            given(six.supports(any())).willReturn(true);
            given(six.quote(any())).willReturn(SourceResult.found(quote(ISIN, "83.88")));
            given(sixHistory.supports(any())).willReturn(true);
            given(sixHistory.history(any(), any())).willReturn(SourceResult.found(List.of()));

            serviceWithHistory(List.of("six"), List.of("six")).backfill(ISIN);

            then(sixHistory).should().history(new SecurityId.Isin(ISIN), TODAY.minusYears(3));
        }

        @Test
        void writes_nothing_and_fetches_no_history_when_there_is_no_current_quote() {
            // Without a current quote there is no currency to stamp the bars with, and the
            // security is either unlisted or the provider is down — nothing to backfill. The
            // history feed must not even be consulted.
            nameTheProviders();
            nameTheHistoryProvider();
            given(six.supports(any())).willReturn(true);
            given(six.quote(any())).willReturn(SourceResult.notFound());

            int stored = serviceWithHistory(List.of("six"), List.of("six")).backfill(ISIN);

            assertThat(stored).isZero();
            then(sixHistory).should(never()).history(any(), any());
            then(writer).shouldHaveNoInteractions();
        }

        @Test
        void writes_nothing_when_the_quote_provider_is_unreachable() {
            // Unavailable says nothing about the security, only about the vendor. A backfill
            // started here would either stamp bars with a missing currency or write nothing at
            // all — so it must not start.
            nameTheProviders();
            nameTheHistoryProvider();
            given(six.supports(any())).willReturn(true);
            given(six.quote(any())).willReturn(SourceResult.unavailable("six: read timed out"));

            int stored = serviceWithHistory(List.of("six"), List.of("six")).backfill(ISIN);

            assertThat(stored).isZero();
            then(sixHistory).should(never()).history(any(), any());
            then(writer).shouldHaveNoInteractions();
        }

        @Test
        void stores_only_the_current_quote_when_the_history_feed_is_empty() {
            // A freshly listed security has a quote but no three-year history yet. The current
            // close is still worth storing; the return is that one row.
            nameTheProviders();
            nameTheHistoryProvider();
            given(six.supports(any())).willReturn(true);
            given(six.quote(any())).willReturn(SourceResult.found(quote(ISIN, "83.88")));
            given(sixHistory.supports(any())).willReturn(true);
            given(sixHistory.history(any(), any())).willReturn(SourceResult.found(List.of()));

            int stored = serviceWithHistory(List.of("six"), List.of("six")).backfill(ISIN);

            assertThat(stored).isEqualTo(1);
            then(writer).should().store(quote(ISIN, "83.88"));
        }

        @Test
        void treats_a_history_provider_that_blows_up_as_no_history_rather_than_failing_the_backfill() {
            // The current quote is already stored by the time history is fetched; a history
            // provider throwing must not undo that or fail the sync. The quote survives, the
            // history is simply empty this run.
            nameTheProviders();
            nameTheHistoryProvider();
            given(six.supports(any())).willReturn(true);
            given(six.quote(any())).willReturn(SourceResult.found(quote(ISIN, "83.88")));
            given(sixHistory.supports(any())).willReturn(true);
            given(sixHistory.history(any(), any()))
                    .willThrow(new IllegalStateException("charts.json changed its shape"));

            int stored = serviceWithHistory(List.of("six"), List.of("six")).backfill(ISIN);

            assertThat(stored).isEqualTo(1);
            then(writer).should().store(quote(ISIN, "83.88"));
        }

        @Test
        void skips_a_malformed_isin_without_spending_a_request() {
            nameTheProviders();
            nameTheHistoryProvider();

            int stored = serviceWithHistory(List.of("six"), List.of("six")).backfill("not-an-isin");

            assertThat(stored).isZero();
            then(six).should(never()).quote(any());
            then(sixHistory).should(never()).history(any(), any());
        }
    }

    // =========================================================================
    // refreshOrBackfillHeld — the nightly job's entry point
    // =========================================================================

    @Nested
    @DisplayName("refreshOrBackfillHeld: backfill a security the first time, then only refresh it")
    class RefreshOrBackfillHeld {

        @Test
        void backfills_a_security_that_has_fewer_than_two_stored_closes() {
            // The first night a position survives: one stored close at most (from position
            // creation), so there is no chart yet. The whole three-year history is fetched.
            nameTheProviders();
            nameTheHistoryProvider();
            given(repository.countByIsin(ISIN)).willReturn(1L);
            given(six.supports(any())).willReturn(true);
            given(six.quote(any())).willReturn(SourceResult.found(quote(ISIN, "83.88")));
            given(sixHistory.supports(any())).willReturn(true);
            given(sixHistory.history(any(), any()))
                    .willReturn(SourceResult.found(List.of(bar(TODAY.minusDays(1), "82.50"))));

            int stored = serviceWithHistory(List.of("six"), List.of("six")).refreshOrBackfillHeld(List.of(ISIN));

            // Current quote plus the one historical bar.
            assertThat(stored).isEqualTo(2);
            then(sixHistory).should().history(any(), any());
        }

        @Test
        void only_refreshes_the_days_close_for_a_security_that_already_has_a_history() {
            // Every night after the first: the chart is already populated, so one cheap quote
            // request is enough. Fetching the whole history again nightly would be waste on an
            // endpoint finyo is merely tolerated on.
            nameTheProviders();
            nameTheHistoryProvider();
            given(repository.countByIsin(ISIN)).willReturn(750L);
            given(six.supports(any())).willReturn(true);
            given(six.quote(any())).willReturn(SourceResult.found(quote(ISIN, "83.88")));

            int stored = serviceWithHistory(List.of("six"), List.of("six")).refreshOrBackfillHeld(List.of(ISIN));

            assertThat(stored).isEqualTo(1);
            then(sixHistory).should(never()).history(any(), any());
            then(writer).should().store(quote(ISIN, "83.88"));
        }

        @Test
        void backfills_at_the_boundary_of_a_single_stored_close() {
            // The boundary is countByIsin < 2. A security with exactly one close still has no
            // chart worth the name, so it is backfilled, not just refreshed.
            nameTheProviders();
            nameTheHistoryProvider();
            given(repository.countByIsin(ISIN)).willReturn(1L);
            given(six.supports(any())).willReturn(true);
            given(six.quote(any())).willReturn(SourceResult.found(quote(ISIN, "83.88")));
            given(sixHistory.supports(any())).willReturn(true);
            given(sixHistory.history(any(), any())).willReturn(SourceResult.found(List.of()));

            serviceWithHistory(List.of("six"), List.of("six")).refreshOrBackfillHeld(List.of(ISIN));

            then(sixHistory).should().history(any(), any());
        }

        @Test
        void refreshes_at_the_boundary_of_two_stored_closes() {
            // Exactly two closes is enough of a series to just refresh from here on.
            nameTheProviders();
            nameTheHistoryProvider();
            given(repository.countByIsin(ISIN)).willReturn(2L);
            given(six.supports(any())).willReturn(true);
            given(six.quote(any())).willReturn(SourceResult.found(quote(ISIN, "83.88")));

            serviceWithHistory(List.of("six"), List.of("six")).refreshOrBackfillHeld(List.of(ISIN));

            then(sixHistory).should(never()).history(any(), any());
        }

        @Test
        void decides_per_security_backfilling_the_new_one_and_refreshing_the_old_one() {
            // A portfolio holds both: a long-standing position and one added yesterday. Each
            // takes the path its own history warrants, in one pass.
            nameTheProviders();
            nameTheHistoryProvider();
            given(repository.countByIsin(ISIN)).willReturn(500L);
            given(repository.countByIsin(OTHER_ISIN)).willReturn(0L);
            given(six.supports(any())).willReturn(true);
            given(six.quote(new SecurityId.Isin(ISIN))).willReturn(SourceResult.found(quote(ISIN, "83.88")));
            given(six.quote(new SecurityId.Isin(OTHER_ISIN)))
                    .willReturn(SourceResult.found(quote(OTHER_ISIN, "144.20")));
            given(sixHistory.supports(any())).willReturn(true);
            given(sixHistory.history(new SecurityId.Isin(OTHER_ISIN), TODAY.minusYears(3)))
                    .willReturn(SourceResult.found(List.of(bar(TODAY.minusDays(1), "142.00"))));

            int stored = serviceWithHistory(List.of("six"), List.of("six"))
                    .refreshOrBackfillHeld(List.of(ISIN, OTHER_ISIN));

            // ISIN: one refreshed close. OTHER_ISIN: current quote plus one bar.
            assertThat(stored).isEqualTo(3);
            then(sixHistory).should().history(new SecurityId.Isin(OTHER_ISIN), TODAY.minusYears(3));
            then(sixHistory).should(never()).history(new SecurityId.Isin(ISIN), TODAY.minusYears(3));
        }
    }
}
