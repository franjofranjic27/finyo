package ch.finyo.integration.six;

import ch.finyo.common.SourceResult;
import ch.finyo.common.money.CurrencyCode;
import ch.finyo.config.ResilienceConfig;
import ch.finyo.integration.ResilientCall;
import ch.finyo.marketdata.MarketDataProperties;
import ch.finyo.marketdata.spi.DataSource;
import ch.finyo.marketdata.spi.Quote;
import ch.finyo.marketdata.spi.SecurityId;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static ch.finyo.common.SourceResults.foundValue;
import static ch.finyo.common.SourceResults.unavailableReason;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SixQuoteAdapter.
 *
 * Against a throwaway loopback HttpServer rather than a hand-built response object, and that
 * is the point rather than a compromise: FQS answers <em>column-oriented</em>
 * ({@code colNames} + parallel {@code rowData} arrays), the price arrives as a JSON number
 * and the trading day as the integer {@code 20260714}. A stubbed FqsResponse would test the
 * mapper against my belief about the wire format instead of against the format.
 *
 * The predecessor of this adapter — {@code SixMarketDataClient} — called a path that was
 * guessed from SIX's marketing pages and never existed. It always failed, the failure was
 * swallowed, and the portfolio quietly showed the purchase price instead. Fixtures here are
 * what SIX actually returns.
 *
 * The load-bearing case is {@link NoPriceIsNotAZeroPrice}: a row without a closing price must
 * become NotFound. A zero would flow into the portfolio total as a real number.
 */
@DisplayName("SixQuoteAdapter")
class SixQuoteAdapterTest {

    private static final String ISIN = "CH0038863350";

    private HttpServer server;
    // volatile: written by the HttpServer's handler thread, read by the test thread
    private volatile String requestedPath;
    private volatile String requestedQuery;
    private volatile int requestCount;
    private volatile String responseBody;
    private volatile int responseStatus;

    @BeforeEach
    void startStubServer() throws IOException {
        responseBody = NESTLE;
        responseStatus = 200;
        requestCount = 0;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestCount++;
            requestedPath = exchange.getRequestURI().getPath();
            requestedQuery = exchange.getRequestURI().getQuery();
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopStubServer() {
        server.stop(0);
    }

    // -------------------------------------------------------------------------
    // Fixtures — the live FQS answers
    // -------------------------------------------------------------------------

    /** Nestlé. Note the shape: parallel arrays, a numeric price, and the date as 20260714. */
    private static final String NESTLE = """
            {"totalRows":1,
             "colNames":["ISIN","ClosingPrice","Currency","LatestTradeDate"],
             "rowData":[["CH0038863350",83.88,"CHF",20260714]]}
            """;

    /** What FQS answers for a security it does not list — every unlisted CSIF 3a fund. */
    private static final String NOT_LISTED = """
            {"totalRows":0,
             "colNames":["ISIN","ClosingPrice","Currency","LatestTradeDate"],
             "rowData":[]}
            """;

    private SixQuoteAdapter newAdapter() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        MarketDataProperties properties = new MarketDataProperties(
                List.of("six"),
                List.of("six"),
                new MarketDataProperties.SixProperties(true, baseUrl),
                new MarketDataProperties.OpenFigiProperties(false, null, null),
                new MarketDataProperties.EodhdProperties(false, null, null));

        ResilienceConfig resilience = new ResilienceConfig();
        ResilientCall resilientCall = new ResilientCall(
                resilience.circuitBreakerRegistry(),
                resilience.retryRegistry(),
                resilience.rateLimiterRegistry(properties));

        // The production bean is Boot's auto-configured builder (it carries the connect/read
        // timeouts); a plain builder is the right stand-in where the stub answers instantly.
        return new SixQuoteAdapter(properties, RestClient.builder(), resilientCall);
    }

    private Quote quoteOf(String isin) {
        return foundValue(newAdapter().quote(new SecurityId.Isin(isin)));
    }

    // =========================================================================
    // Provider contract
    // =========================================================================

    @Test
    void is_registered_under_the_configuration_key_six() {
        assertThat(newAdapter().name()).isEqualTo("six");
    }

    // =========================================================================
    // Mapping the column-oriented payload
    // =========================================================================

    @Nested
    @DisplayName("maps the column-oriented FQS payload")
    class Mapping {

        @Test
        void maps_the_parallel_arrays_onto_a_quote() {
            Quote quote = quoteOf(ISIN);

            assertThat(quote.isin()).isEqualTo(ISIN);
            assertThat(quote.price()).isEqualByComparingTo("83.88");
            assertThat(quote.currency()).isEqualTo(CurrencyCode.CHF);
            assertThat(quote.source()).isEqualTo(DataSource.SIX);
            assertThat(quote.retrievedAt()).isNotNull();
        }

        @Test
        void reads_the_trading_day_out_of_the_yyyyMMdd_integer_FQS_sends() {
            // FQS types its cells loosely: the date arrives as the JSON number 20260714, not
            // as a string and not as an ISO date. Read it wrong and every price is filed
            // under the wrong day — or, worse, under none, and then it is never stored.
            assertThat(quoteOf(ISIN).asOf()).isEqualTo(LocalDate.of(2026, 7, 14));
        }

        @Test
        void marks_every_quote_as_delayed_because_the_free_feed_is() {
            // SIX serves the free FQS feed 15 minutes behind. It is not a live quote, and
            // calling it one would be a small lie told very often.
            assertThat(quoteOf(ISIN).delayed()).isTrue();
        }

        @Test
        void keeps_a_price_in_its_own_currency_rather_than_assuming_francs() {
            // A price is a number *in a currency*. Summing a USD quote into a CHF total is the
            // bug this project already shipped once.
            responseBody = """
                    {"totalRows":1,
                     "colNames":["ISIN","ClosingPrice","Currency","LatestTradeDate"],
                     "rowData":[["IE00B4L5Y983",144.2,"USD",20260714]]}
                    """;

            Quote quote = quoteOf("IE00B4L5Y983");

            assertThat(quote.currency()).isEqualTo(new CurrencyCode("USD"));
            assertThat(quote.price()).isEqualByComparingTo("144.2");
        }

        @Test
        void yields_a_quote_without_a_currency_rather_than_inventing_one() {
            // The writer refuses to store this (a price without a currency is a number without
            // a meaning), which is precisely why the adapter must not paper over it with CHF.
            responseBody = """
                    {"totalRows":1,
                     "colNames":["ISIN","ClosingPrice","Currency","LatestTradeDate"],
                     "rowData":[["CH0038863350",83.88,null,20260714]]}
                    """;

            assertThat(quoteOf(ISIN).currency()).isNull();
        }

        @Test
        void yields_a_quote_without_a_trading_day_when_FQS_omits_one() {
            // Same contract: reported honestly and refused by the writer, rather than filed
            // under today and passed off as a fresh close.
            responseBody = """
                    {"totalRows":1,
                     "colNames":["ISIN","ClosingPrice","Currency","LatestTradeDate"],
                     "rowData":[["CH0038863350",83.88,"CHF",null]]}
                    """;

            assertThat(quoteOf(ISIN).asOf()).isNull();
        }

        @Test
        void yields_a_quote_without_a_trading_day_when_FQS_sends_an_unreadable_one() {
            // Unofficial endpoint, no contract, no stability promise: a date we cannot parse
            // is a question of when, not if. It must not throw, and it must not be guessed at.
            responseBody = """
                    {"totalRows":1,
                     "colNames":["ISIN","ClosingPrice","Currency","LatestTradeDate"],
                     "rowData":[["CH0038863350",83.88,"CHF","14.07.2026"]]}
                    """;

            assertThat(quoteOf(ISIN).asOf()).isNull();
        }
    }

    // =========================================================================
    // No price is not a zero price
    // =========================================================================

    @Nested
    @DisplayName("a missing price is 'no quote', never a price of zero")
    class NoPriceIsNotAZeroPrice {

        @Test
        void reports_NotFound_when_the_row_carries_no_closing_price() {
            // The load-bearing case. An instrument that is listed but has never traded comes
            // back with the column empty. A zero here would not be an empty cell any more —
            // it would be a number, and it would be summed into the portfolio total as one.
            responseBody = """
                    {"totalRows":1,
                     "colNames":["ISIN","ClosingPrice","Currency","LatestTradeDate"],
                     "rowData":[["CH0038863350",null,"CHF",20260714]]}
                    """;

            SourceResult<Quote> result = newAdapter().quote(new SecurityId.Isin(ISIN));

            assertThat(result).isEqualTo(SourceResult.notFound());
        }

        @Test
        void reports_NotFound_when_the_closing_price_is_zero() {
            // The case the nested class is named for, and the one a null-only test quietly
            // failed to cover. SIX returns a literal 0 for an instrument that is listed but has
            // never traded. Unlike an empty cell it parses cleanly to BigDecimal.ZERO, sails
            // past a null check, and would land in the portfolio as a value of 0 and a −100%
            // return that looks entirely real — the fresh-looking wrong number this module
            // exists to prevent.
            responseBody = """
                    {"totalRows":1,
                     "colNames":["ISIN","ClosingPrice","Currency","LatestTradeDate"],
                     "rowData":[["CH0038863350",0,"CHF",20260714]]}
                    """;

            assertThat(newAdapter().quote(new SecurityId.Isin(ISIN)))
                    .isEqualTo(SourceResult.notFound());
        }

        @Test
        void reports_NotFound_when_the_closing_price_is_negative() {
            // No instrument trades below zero; a negative close is a vendor glitch, not a
            // price, and summing it into the portfolio total would understate it as a real loss.
            responseBody = """
                    {"totalRows":1,
                     "colNames":["ISIN","ClosingPrice","Currency","LatestTradeDate"],
                     "rowData":[["CH0038863350",-5,"CHF",20260714]]}
                    """;

            assertThat(newAdapter().quote(new SecurityId.Isin(ISIN)))
                    .isEqualTo(SourceResult.notFound());
        }

        @Test
        void reports_NotFound_when_the_price_column_is_missing_from_the_payload_entirely() {
            responseBody = """
                    {"totalRows":1,
                     "colNames":["ISIN","Currency","LatestTradeDate"],
                     "rowData":[["CH0038863350","CHF",20260714]]}
                    """;

            assertThat(newAdapter().quote(new SecurityId.Isin(ISIN)))
                    .isEqualTo(SourceResult.notFound());
        }

        @Test
        void reports_NotFound_when_the_price_is_not_a_number() {
            responseBody = """
                    {"totalRows":1,
                     "colNames":["ISIN","ClosingPrice","Currency","LatestTradeDate"],
                     "rowData":[["CH0038863350","n/a","CHF",20260714]]}
                    """;

            assertThat(newAdapter().quote(new SecurityId.Isin(ISIN)))
                    .isEqualTo(SourceResult.notFound());
        }
    }

    // =========================================================================
    // Request building
    // =========================================================================

    @Nested
    @DisplayName("builds the FQS request")
    class RequestBuilding {

        @Test
        void asks_the_movie_endpoint_for_the_price_columns_filtered_by_isin() {
            newAdapter().quote(new SecurityId.Isin(ISIN));

            assertThat(requestedPath).isEqualTo("/movie.json");
            assertThat(requestedQuery)
                    .contains("where=ISIN=" + ISIN)
                    .contains("ISIN,ClosingPrice,Currency,LatestTradeDate");
            // One quote is one request: a successful call must not be retried against a source
            // finyo is merely tolerated on.
            assertThat(requestCount).isEqualTo(1);
        }
    }

    // =========================================================================
    // NotFound is about the security; Unavailable is about SIX
    // =========================================================================

    @Nested
    @DisplayName("distinguishes an unpriced security from an unreachable SIX")
    class NotFoundVersusUnavailable {

        @Test
        void reports_NotFound_when_six_does_not_list_the_security() {
            // totalRows: 0 — the answer for every unlisted 3a fund. A durable fact about the
            // security, and the reason the portfolio may legitimately fall back to a manual
            // price and label it as one.
            responseBody = NOT_LISTED;

            SourceResult<Quote> result = newAdapter().quote(new SecurityId.Isin("CH0214967314"));

            assertThat(result).isEqualTo(SourceResult.notFound());
        }

        @Test
        void reports_Unavailable_when_FQS_answers_with_a_server_error() {
            // Says nothing about the security. Reported as NotFound it would be
            // indistinguishable from "this fund has no price", and MarketDataService would
            // stop looking — while the last good close sat in the database going stale.
            responseStatus = 500;
            responseBody = "{\"error\":\"boom\"}";

            SourceResult<Quote> result = newAdapter().quote(new SecurityId.Isin(ISIN));

            assertThat(unavailableReason(result)).startsWith("six:");
        }

        @Test
        void reports_Unavailable_when_FQS_answers_with_something_that_is_not_json() {
            // A payload we can no longer read means SIX changed its wire format. That is a
            // vendor failure and must trip the circuit breaker like any other.
            responseBody = "<html><body>503 Service Unavailable</body></html>";

            SourceResult<Quote> result = newAdapter().quote(new SecurityId.Isin(ISIN));

            assertThat(unavailableReason(result)).startsWith("six:");
        }

        @Test
        void reports_Unavailable_when_SIX_announces_rows_but_sends_no_column_names() {
            // The dangerous shape: it parses, and it is meaningless. FQS is column-oriented,
            // so without colNames the rowData cannot be interpreted at all. Calling that
            // "no price" would let a format change silently blank every price in the system.
            responseBody = "{\"totalRows\":1,\"rowData\":[[\"CH0038863350\",83.88]]}";

            SourceResult<Quote> result = newAdapter().quote(new SecurityId.Isin(ISIN));

            assertThat(unavailableReason(result)).startsWith("six:");
        }
    }
}
