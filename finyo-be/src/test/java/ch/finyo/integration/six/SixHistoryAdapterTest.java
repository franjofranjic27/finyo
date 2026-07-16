package ch.finyo.integration.six;

import ch.finyo.common.SourceResult;
import ch.finyo.config.ResilienceConfig;
import ch.finyo.integration.ResilientCall;
import ch.finyo.marketdata.MarketDataProperties;
import ch.finyo.marketdata.spi.PriceBar;
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

import static ch.finyo.common.SourceResults.unavailableReason;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SixHistoryAdapter.
 *
 * Against a throwaway loopback HttpServer rather than a stubbed response object, exactly like
 * SixQuoteAdapterTest and for the same reason: charts.json has a shape all its own — not the
 * column-oriented {@code colNames}/{@code rowData} of movie.json, but parallel {@code Date} and
 * {@code Close} arrays nested under each valor, with the day as the integer {@code 20260105}. A
 * hand-built response would test the mapper against my belief about the wire format instead of
 * against the format, and the capitalised {@code Date}/{@code Close} keys are precisely the sort
 * of thing that only holds up if the real deserialisation runs.
 *
 * The load-bearing distinction, as everywhere in this module: an empty history is a Found empty
 * list (the security is known, it simply has no closes in the window), while an unreachable SIX
 * is Unavailable. Only the former is a fact about the security.
 */
@DisplayName("SixHistoryAdapter")
class SixHistoryAdapterTest {

    private static final String ISIN = "IE00B4L5Y983";
    private static final LocalDate FROM = LocalDate.of(2023, 1, 1);

    private HttpServer server;
    // volatile: written by the HttpServer's handler thread, read by the test thread
    private volatile String requestedPath;
    private volatile String requestedQuery;
    private volatile int requestCount;
    private volatile String responseBody;
    private volatile int responseStatus;

    @BeforeEach
    void startStubServer() throws IOException {
        responseBody = THREE_DAYS;
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
    // Fixtures — the live charts.json shape
    // -------------------------------------------------------------------------

    /**
     * Three trading days. Note the shape: parallel arrays, the day as the integer 20260105, and
     * no {@code totalRows} — charts.json does not carry one.
     */
    private static final String THREE_DAYS = """
            {"valors":[{"ISIN":"IE00B4L5Y983","data":{
                "Date":  [20260105, 20260106, 20260107],
                "Close": [144.20,   143.94,   145.10]}}]}
            """;

    /** The security is known but has no closes in the window — a legitimate empty answer. */
    private static final String EMPTY_ARRAYS = """
            {"valors":[{"ISIN":"IE00B4L5Y983","data":{"Date":[],"Close":[]}}]}
            """;

    private SixHistoryAdapter newAdapter() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        MarketDataProperties properties = new MarketDataProperties(
                List.of("six"),
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
        return new SixHistoryAdapter(properties, RestClient.builder(), resilientCall);
    }

    private List<PriceBar> barsOf(String isin) {
        SourceResult<List<PriceBar>> result = newAdapter().history(new SecurityId.Isin(isin), FROM);
        assertThat(result).isInstanceOf(SourceResult.Found.class);
        return ((SourceResult.Found<List<PriceBar>>) result).value();
    }

    // =========================================================================
    // Provider contract
    // =========================================================================

    @Test
    void is_registered_under_the_configuration_key_six() {
        // Same key as the quote and reference adapters: one vendor, one name in the chain.
        assertThat(newAdapter().name()).isEqualTo("six");
    }

    // =========================================================================
    // Mapping the parallel Date/Close arrays
    // =========================================================================

    @Nested
    @DisplayName("zips the parallel Date/Close arrays into bars")
    class Mapping {

        @Test
        void pairs_each_date_with_the_close_at_the_same_index() {
            List<PriceBar> bars = barsOf(ISIN);

            assertThat(bars).hasSize(3);
            assertThat(bars).extracting(PriceBar::date).containsExactly(
                    LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 7));
            assertThat(bars).extracting(bar -> bar.close().stripTrailingZeros()).containsExactly(
                    new java.math.BigDecimal("144.2"),
                    new java.math.BigDecimal("143.94"),
                    new java.math.BigDecimal("145.1"));
        }

        @Test
        void reads_the_yyyyMMdd_integer_into_a_local_date() {
            // charts.json types its day as the number 20260105, not an ISO string. Read it
            // wrong and every close is filed under the wrong day.
            assertThat(barsOf(ISIN).getFirst().date()).isEqualTo(LocalDate.of(2026, 1, 5));
        }

        @Test
        void copes_with_a_payload_that_carries_no_totalRows() {
            // Unlike movie.json, charts.json sends no totalRows field at all. The mapper must
            // not depend on one — the THREE_DAYS fixture has none, and it still parses.
            assertThat(barsOf(ISIN)).isNotEmpty();
        }
    }

    // =========================================================================
    // A zero close is not a price
    // =========================================================================

    @Nested
    @DisplayName("a non-positive close is dropped, never carried as a real number")
    class NonPositiveClosesDropped {

        @Test
        void drops_a_bar_whose_close_is_zero() {
            // SIX emits 0 for a day an instrument was listed but did not trade. A zero flowing
            // into a chart and a valuation would read as a real close of nothing.
            responseBody = """
                    {"valors":[{"ISIN":"IE00B4L5Y983","data":{
                        "Date":  [20260105, 20260106, 20260107],
                        "Close": [144.20,   0,        145.10]}}]}
                    """;

            List<PriceBar> bars = barsOf(ISIN);

            assertThat(bars).hasSize(2);
            assertThat(bars).extracting(PriceBar::date)
                    .containsExactly(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7));
        }

        @Test
        void drops_a_bar_whose_close_is_negative() {
            // No instrument trades below zero; a negative close is a vendor glitch, not a price.
            responseBody = """
                    {"valors":[{"ISIN":"IE00B4L5Y983","data":{
                        "Date":  [20260105, 20260106],
                        "Close": [-5,       145.10]}}]}
                    """;

            assertThat(barsOf(ISIN)).extracting(PriceBar::date)
                    .containsExactly(LocalDate.of(2026, 1, 6));
        }
    }

    // =========================================================================
    // Empty is Found-empty, unreachable is Unavailable
    // =========================================================================

    @Nested
    @DisplayName("distinguishes a security with no closes from an unreachable SIX")
    class FoundEmptyVersusUnavailable {

        @Test
        void returns_a_found_empty_list_when_the_window_holds_no_closes() {
            // A Found empty list, not Unavailable: the security is known, it simply has nothing
            // in the requested window. Reporting it as unreachable would trip the circuit
            // breaker over a perfectly normal answer.
            responseBody = EMPTY_ARRAYS;

            SourceResult<List<PriceBar>> result = newAdapter().history(new SecurityId.Isin(ISIN), FROM);

            assertThat(result).isEqualTo(SourceResult.found(List.of()));
        }

        @Test
        void returns_a_found_empty_list_when_the_data_object_is_absent() {
            // No "data" at all — again a legitimate "nothing here", not a failure.
            responseBody = """
                    {"valors":[{"ISIN":"IE00B4L5Y983"}]}
                    """;

            SourceResult<List<PriceBar>> result = newAdapter().history(new SecurityId.Isin(ISIN), FROM);

            assertThat(result).isEqualTo(SourceResult.found(List.of()));
        }

        @Test
        void returns_a_found_empty_list_when_no_valor_is_returned() {
            responseBody = "{\"valors\":[]}";

            SourceResult<List<PriceBar>> result = newAdapter().history(new SecurityId.Isin(ISIN), FROM);

            assertThat(result).isEqualTo(SourceResult.found(List.of()));
        }

        @Test
        void reports_Unavailable_when_charts_answers_with_a_server_error() {
            // Says nothing about the security. A 500 must trip the circuit breaker, not be
            // mistaken for "this security has no history".
            responseStatus = 500;
            responseBody = "{\"error\":\"boom\"}";

            SourceResult<List<PriceBar>> result = newAdapter().history(new SecurityId.Isin(ISIN), FROM);

            assertThat(unavailableReason(result)).startsWith("six:");
        }

        @Test
        void reports_Unavailable_when_charts_answers_with_something_that_is_not_json() {
            // A payload we can no longer read means SIX changed its wire format — a vendor
            // failure, handled like any other.
            responseBody = "<html><body>503 Service Unavailable</body></html>";

            SourceResult<List<PriceBar>> result = newAdapter().history(new SecurityId.Isin(ISIN), FROM);

            assertThat(unavailableReason(result)).startsWith("six:");
        }
    }

    // =========================================================================
    // Request building
    // =========================================================================

    @Nested
    @DisplayName("builds the charts.json request")
    class RequestBuilding {

        @Test
        void asks_charts_for_daily_closes_filtered_by_isin_from_the_given_date() {
            newAdapter().history(new SecurityId.Isin(ISIN), FROM);

            assertThat(requestedPath).isEqualTo("/charts.json");
            assertThat(requestedQuery)
                    .contains("where=ISIN=" + ISIN)
                    .contains("select=ISIN,ClosingPrice")
                    // netting=1440 is one bar per day; without it the feed is intraday.
                    .contains("netting=1440")
                    .contains("fromdate=20230101");
            // One history call is one request — a successful call must not be retried against a
            // source finyo is merely tolerated on.
            assertThat(requestCount).isEqualTo(1);
        }
    }
}
