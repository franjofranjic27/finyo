package ch.finyo.marketdata;

import ch.finyo.common.SourceResult;
import ch.finyo.common.SwissTime;
import ch.finyo.common.money.CurrencyCode;
import ch.finyo.marketdata.spi.DataSource;
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
    private InstrumentPriceRepository repository;

    @Mock
    private InstrumentPriceWriter writer;

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    /**
     * The bean list is deliberately [six, eodhd] everywhere, so a test that configures the
     * reverse order proves configuration wins rather than luck.
     */
    private MarketDataService serviceWithChain(String... quoteProviders) {
        MarketDataProperties properties = new MarketDataProperties(
                List.of(),
                List.of(quoteProviders),
                new MarketDataProperties.SixProperties(true, "http://six.invalid"),
                new MarketDataProperties.OpenFigiProperties(false, null, null),
                new MarketDataProperties.EodhdProperties(false, null, null));

        return new MarketDataService(List.of(six, eodhd), properties, repository, writer);
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

    private void nameTheProviders() {
        given(six.name()).willReturn("six");
        given(eodhd.name()).willReturn("eodhd");
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
}
