package ch.finyo.integration.bazg;

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
 * Unit tests for BazgFxAdapter, against a throwaway loopback HttpServer.
 *
 * BAZG returns CHF-per-unit already, as a <em>string</em>, and the day in {@code dd.MM.yyyy}. No
 * inversion — the risk here is the opposite one: inverting it would be wrong. It is filed as
 * OFFICIAL_CH so it can never be mistaken for a valuation rate.
 */
@DisplayName("BazgFxAdapter")
class BazgFxAdapterTest {

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

    private BazgFxAdapter newAdapter() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        FxProperties properties = new FxProperties(
                new FxProperties.Vendor(false, null),
                new FxProperties.Vendor(true, baseUrl));
        return new BazgFxAdapter(properties, RestClient.builder(), resilientCall());
    }

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

    @Test
    void is_registered_under_the_configuration_key_bazg_as_an_official_provider() {
        responseBody = "{}";
        BazgFxAdapter adapter = newAdapter();
        assertThat(adapter.name()).isEqualTo("bazg");
        assertThat(adapter.type()).isEqualTo(FxRateType.OFFICIAL_CH);
    }

    @Test
    void takes_the_rate_as_chf_per_unit_without_inverting_it() {
        responseBody = """
                {"date":"14.07.2026","base":"CHF","rates":[{"symbol":"EUR","rate":"0.93661"},{"symbol":"USD","rate":"0.80000"}]}
                """;

        FxRate rate = foundValue(newAdapter().rate(EUR, LocalDate.of(2026, 7, 14)));

        assertThat(rate.getCurrency()).isEqualTo("EUR");
        assertThat(rate.getChfPerUnit()).isEqualByComparingTo("0.93661");
        assertThat(rate.getRateDate()).isEqualTo(LocalDate.of(2026, 7, 14));
        assertThat(rate.getRateType()).isEqualTo(FxRateType.OFFICIAL_CH);
        assertThat(rate.getSource()).isEqualTo("bazg");
    }

    @Test
    void asks_the_rates_endpoint_for_the_requested_day() {
        responseBody = """
                {"date":"14.07.2026","base":"CHF","rates":[{"symbol":"EUR","rate":"0.93661"}]}
                """;

        newAdapter().rate(EUR, LocalDate.of(2026, 7, 14));

        assertThat(requestedPath).isEqualTo("/rates");
        assertThat(requestedQuery).isEqualTo("d=20260714");
    }

    @Test
    void reports_NotFound_when_the_currency_is_absent_from_the_list() {
        responseBody = """
                {"date":"14.07.2026","base":"CHF","rates":[{"symbol":"USD","rate":"0.80000"}]}
                """;

        assertThat(newAdapter().rate(EUR, LocalDate.of(2026, 7, 14)))
                .isEqualTo(SourceResult.notFound());
    }

    @Test
    void reports_Unavailable_when_bazg_answers_with_a_server_error() {
        responseStatus = 503;
        responseBody = "down";

        assertThat(unavailableReason(newAdapter().rate(EUR, LocalDate.of(2026, 7, 14))))
                .startsWith("bazg:");
    }

    @Test
    void has_no_range_endpoint_so_a_range_query_is_empty() {
        responseBody = "{}";

        assertThat(newAdapter().rates(EUR, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)))
                .isEmpty();
    }
}
