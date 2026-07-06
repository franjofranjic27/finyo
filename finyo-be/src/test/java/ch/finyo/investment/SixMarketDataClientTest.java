package ch.finyo.investment;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SixMarketDataClient.
 *
 * The client builds its RestClient internally from SixMarketDataProperties,
 * so the tests run against a throwaway JDK HttpServer bound to an ephemeral
 * loopback port. This exercises the real request building (path, auth header)
 * and the real JSON deserialization instead of mocking the HTTP layer away.
 *
 * Focus areas:
 *   1. Guard clauses: null/blank identifiers never trigger a request.
 *   2. Response mapping: primary and fallback field names (lastPrice/close,
 *      changePercent/performancePercent1Day), defaults, safeDecimal parsing.
 *   3. Failure handling: HTTP errors and empty bodies degrade to Optional.empty().
 */
class SixMarketDataClientTest {

    private HttpServer server;
    private String requestedPath;
    private String authorizationHeader;
    private String responseBody;
    private int responseStatus;

    @BeforeEach
    void startStubServer() throws IOException {
        responseBody = "{}";
        responseStatus = 200;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestedPath = exchange.getRequestURI().getPath();
            authorizationHeader = exchange.getRequestHeaders().getFirst("Authorization");
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

    private SixMarketDataClient newClient() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return new SixMarketDataClient(new SixMarketDataProperties(baseUrl, "test-api-key"));
    }

    // =========================================================================
    // Guard clauses
    // =========================================================================

    @Test
    void fetch_with_null_identifier_returns_empty_without_calling_the_api() {
        Optional<MarketDataResponse> result = newClient().fetchByValorOrIsin(null);

        assertThat(result).isEmpty();
        assertThat(requestedPath).isNull();
    }

    @Test
    void fetch_with_blank_identifier_returns_empty_without_calling_the_api() {
        Optional<MarketDataResponse> result = newClient().fetchByValorOrIsin("   ");

        assertThat(result).isEmpty();
        assertThat(requestedPath).isNull();
    }

    // =========================================================================
    // Request building
    // =========================================================================

    @Test
    void fetch_requests_the_latest_eod_closing_price_with_a_bearer_token() {
        responseBody = "{\"lastPrice\": 92.5}";

        newClient().fetchByValorOrIsin("3886335");

        assertThat(requestedPath).isEqualTo("/instruments/3886335/eod-closing-prices/latest");
        assertThat(authorizationHeader).isEqualTo("Bearer test-api-key");
    }

    // =========================================================================
    // Response mapping
    // =========================================================================

    @Test
    void fetch_maps_a_fully_populated_six_response() {
        responseBody = """
                {
                  "valor": "3886335",
                  "isin": "CH0038863350",
                  "ticker": "NESN",
                  "name": "Nestle SA",
                  "exchange": "SIX Swiss Exchange",
                  "currency": "CHF",
                  "lastPrice": 92.50,
                  "changePercent": -1.25,
                  "performancePercent1Week": 0.5,
                  "performancePercent1Month": 2.75,
                  "performancePercentYtd": 5.0,
                  "performancePercent1Year": 8.25,
                  "high52w": 110.00,
                  "low52w": 80.00,
                  "marketCap": 250000000000,
                  "dividendYield": 3.2,
                  "instrumentType": "SHARE",
                  "sector": "Consumer Staples",
                  "ter": 0.15
                }
                """;

        MarketDataResponse result = newClient().fetchByValorOrIsin("3886335").orElseThrow();

        assertThat(result.valor()).isEqualTo("3886335");
        assertThat(result.isin()).isEqualTo("CH0038863350");
        assertThat(result.ticker()).isEqualTo("NESN");
        assertThat(result.name()).isEqualTo("Nestle SA");
        assertThat(result.exchange()).isEqualTo("SIX Swiss Exchange");
        assertThat(result.currency()).isEqualTo("CHF");
        assertThat(result.lastPrice()).isEqualByComparingTo("92.5");
        assertThat(result.change1d()).isNull();
        assertThat(result.change1dPct()).isEqualTo(-1.25);
        assertThat(result.change1wPct()).isEqualByComparingTo("0.5");
        assertThat(result.change1mPct()).isEqualByComparingTo("2.75");
        assertThat(result.changeYtdPct()).isEqualByComparingTo("5.0");
        assertThat(result.change1yPct()).isEqualByComparingTo("8.25");
        assertThat(result.high52w()).isEqualByComparingTo("110.00");
        assertThat(result.low52w()).isEqualByComparingTo("80.00");
        assertThat(result.marketCap()).isEqualByComparingTo("250000000000");
        assertThat(result.dividendYield()).isEqualByComparingTo("3.2");
        assertThat(result.instrumentType()).isEqualTo("SHARE");
        assertThat(result.sector()).isEqualTo("Consumer Staples");
        assertThat(result.ter()).isEqualByComparingTo("0.15");
        assertThat(result.dataAsOf()).isNotNull();
        assertThat(result.fromCache()).isFalse();
    }

    @Test
    void fetch_falls_back_to_close_and_daily_performance_when_primary_fields_are_missing() {
        responseBody = "{\"close\": 45.10, \"performancePercent1Day\": 0.8}";

        MarketDataResponse result = newClient().fetchByValorOrIsin("123").orElseThrow();

        assertThat(result.lastPrice()).isEqualByComparingTo("45.10");
        assertThat(result.change1dPct()).isEqualTo(0.8);
    }

    @Test
    void fetch_applies_defaults_for_missing_exchange_currency_and_optional_numbers() {
        responseBody = "{\"lastPrice\": 10}";

        MarketDataResponse result = newClient().fetchByValorOrIsin("123").orElseThrow();

        assertThat(result.exchange()).isEqualTo("SIX");
        assertThat(result.currency()).isEqualTo("CHF");
        assertThat(result.valor()).isEmpty();
        assertThat(result.change1dPct()).isNull();
        assertThat(result.high52w()).isNull();
        assertThat(result.ter()).isNull();
    }

    @Test
    void fetch_ignores_non_numeric_values_in_optional_decimal_fields() {
        responseBody = "{\"lastPrice\": 10, \"high52w\": \"n/a\"}";

        MarketDataResponse result = newClient().fetchByValorOrIsin("123").orElseThrow();

        assertThat(result.lastPrice()).isEqualByComparingTo("10");
        assertThat(result.high52w()).isNull();
    }

    // =========================================================================
    // Failure handling
    // =========================================================================

    @Test
    void fetch_returns_empty_when_the_api_responds_with_a_server_error() {
        responseStatus = 500;
        responseBody = "{\"error\": \"boom\"}";

        Optional<MarketDataResponse> result = newClient().fetchByValorOrIsin("123");

        assertThat(result).isEmpty();
    }

    @Test
    void fetch_returns_empty_when_the_instrument_is_unknown() {
        responseStatus = 404;
        responseBody = "{\"error\": \"not found\"}";

        Optional<MarketDataResponse> result = newClient().fetchByValorOrIsin("does-not-exist");

        assertThat(result).isEmpty();
    }
}
