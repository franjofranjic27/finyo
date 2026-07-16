package ch.finyo.integration.frankfurter;

import ch.finyo.common.SourceResult;
import ch.finyo.common.money.CurrencyCode;
import ch.finyo.config.ResilienceConfig;
import ch.finyo.fx.FxProperties;
import ch.finyo.fx.FxRate;
import ch.finyo.fx.FxRateType;
import ch.finyo.integration.ResilientCall;
import ch.finyo.marketdata.MarketDataProperties;
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
 * Unit tests for FrankfurterFxAdapter, against a throwaway loopback HttpServer.
 *
 * The load-bearing behaviour is the <b>inversion</b>: Frankfurter answers {@code base=CHF} with
 * units-per-CHF (EUR 1.08 per CHF), and the domain stores CHF-per-unit. Getting it upside down
 * would not throw — it would quietly value a EUR portfolio at 1.08× instead of 0.93×. The stub
 * returns what Frankfurter actually returns so the mapper is tested against the wire format, not
 * against a belief about it.
 */
@DisplayName("FrankfurterFxAdapter")
class FrankfurterFxAdapterTest {

    private static final CurrencyCode EUR = new CurrencyCode("EUR");

    private HttpServer server;
    private volatile String requestedPath;
    private volatile String requestedQuery;
    private volatile String responseBody;
    private volatile int responseStatus;

    @BeforeEach
    void startStubServer() throws IOException {
        responseStatus = 200;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
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

    private FrankfurterFxAdapter newAdapter() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        FxProperties properties = new FxProperties(
                new FxProperties.Vendor(true, baseUrl),
                new FxProperties.Vendor(false, null));
        return new FrankfurterFxAdapter(properties, RestClient.builder(), resilientCall());
    }

    /** The resilience registries need a MarketDataProperties only to size OpenFIGI's limiter. */
    private static ResilientCall resilientCall() {
        ResilienceConfig resilience = new ResilienceConfig();
        MarketDataProperties md = new MarketDataProperties(
                List.of(), List.of(), List.of(),
                new MarketDataProperties.SixProperties(false, null),
                new MarketDataProperties.OpenFigiProperties(false, null, null),
                new MarketDataProperties.EodhdProperties(false, null, null));
        return new ResilientCall(resilience.circuitBreakerRegistry(),
                resilience.retryRegistry(), resilience.rateLimiterRegistry(md));
    }

    // =========================================================================
    // Provider contract
    // =========================================================================

    @Test
    void is_registered_under_the_configuration_key_frankfurter_as_a_mid_provider() {
        responseBody = "{}";
        FrankfurterFxAdapter adapter = newAdapter();
        assertThat(adapter.name()).isEqualTo("frankfurter");
        assertThat(adapter.type()).isEqualTo(FxRateType.MID);
    }

    // =========================================================================
    // Inversion — the whole point
    // =========================================================================

    @Nested
    @DisplayName("inverts units-per-CHF into CHF-per-unit")
    class Inversion {

        @Test
        void maps_the_single_day_response_and_inverts_the_rate() {
            // 1.08 EUR per CHF → 1/1.08 = 0.92592593 CHF per EUR.
            responseBody = """
                    {"amount":1.0,"base":"CHF","date":"2026-07-14","rates":{"EUR":1.08}}
                    """;

            FxRate rate = foundValue(newAdapter().rate(EUR, LocalDate.of(2026, 7, 14)));

            assertThat(rate.getCurrency()).isEqualTo("EUR");
            assertThat(rate.getChfPerUnit()).isEqualByComparingTo("0.92592593");
            assertThat(rate.getRateDate()).isEqualTo(LocalDate.of(2026, 7, 14));
            assertThat(rate.getRateType()).isEqualTo(FxRateType.MID);
            assertThat(rate.getSource()).isEqualTo("frankfurter");
        }

        @Test
        void files_the_rate_under_the_day_frankfurter_reports_not_the_day_requested() {
            // A weekend has no rate; Frankfurter answers with the previous working day, and that
            // is the day the rate belongs to — filing it under the requested Saturday would be wrong.
            responseBody = """
                    {"amount":1.0,"base":"CHF","date":"2026-07-10","rates":{"EUR":1.08}}
                    """;

            FxRate rate = foundValue(newAdapter().rate(EUR, LocalDate.of(2026, 7, 12)));

            assertThat(rate.getRateDate()).isEqualTo(LocalDate.of(2026, 7, 10));
        }

        @Test
        void asks_the_dated_endpoint_with_chf_as_base() {
            responseBody = """
                    {"amount":1.0,"base":"CHF","date":"2026-07-14","rates":{"EUR":1.08}}
                    """;

            newAdapter().rate(EUR, LocalDate.of(2026, 7, 14));

            assertThat(requestedPath).isEqualTo("/2026-07-14");
            assertThat(requestedQuery).contains("base=CHF").contains("symbols=EUR");
        }
    }

    // =========================================================================
    // Range backfill
    // =========================================================================

    @Nested
    @DisplayName("backfills a date range, one inverted rate per trading day")
    class Range {

        @Test
        void maps_every_day_in_the_range_and_inverts_each() {
            responseBody = """
                    {"amount":1.0,"base":"CHF","start_date":"2025-01-02","end_date":"2025-01-03",
                     "rates":{"2025-01-02":{"EUR":1.04},"2025-01-03":{"EUR":1.05}}}
                    """;

            List<FxRate> rates = newAdapter().rates(EUR, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

            assertThat(rates).hasSize(2);
            assertThat(rates).extracting(FxRate::getRateDate)
                    .containsExactlyInAnyOrder(LocalDate.of(2025, 1, 2), LocalDate.of(2025, 1, 3));
            assertThat(rates).allSatisfy(r -> assertThat(r.getRateType()).isEqualTo(FxRateType.MID));
        }

        @Test
        void asks_the_range_endpoint() {
            responseBody = "{\"base\":\"CHF\",\"rates\":{}}";

            newAdapter().rates(EUR, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

            assertThat(requestedPath).isEqualTo("/2025-01-01..2025-12-31");
        }
    }

    // =========================================================================
    // NotFound vs Unavailable
    // =========================================================================

    @Nested
    @DisplayName("distinguishes a missing symbol from an unreachable Frankfurter")
    class NotFoundVersusUnavailable {

        @Test
        void reports_NotFound_when_the_response_omits_the_requested_symbol() {
            responseBody = """
                    {"amount":1.0,"base":"CHF","date":"2026-07-14","rates":{"USD":0.80}}
                    """;

            assertThat(newAdapter().rate(EUR, LocalDate.of(2026, 7, 14)))
                    .isEqualTo(SourceResult.notFound());
        }

        @Test
        void reports_NotFound_when_the_rate_is_zero_rather_than_dividing_by_it() {
            // Inverting a zero would throw; a zero rate is a vendor glitch, reported as no rate.
            responseBody = """
                    {"amount":1.0,"base":"CHF","date":"2026-07-14","rates":{"EUR":0}}
                    """;

            assertThat(newAdapter().rate(EUR, LocalDate.of(2026, 7, 14)))
                    .isEqualTo(SourceResult.notFound());
        }

        @Test
        void reports_Unavailable_when_frankfurter_answers_with_a_server_error() {
            responseStatus = 500;
            responseBody = "{\"error\":\"boom\"}";

            SourceResult<FxRate> result = newAdapter().rate(EUR, LocalDate.of(2026, 7, 14));

            assertThat(unavailableReason(result)).startsWith("frankfurter:");
        }

        @Test
        void returns_an_empty_range_rather_than_throwing_when_frankfurter_is_unreachable() {
            responseStatus = 500;
            responseBody = "boom";

            assertThat(newAdapter().rates(EUR, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)))
                    .isEmpty();
        }
    }
}
