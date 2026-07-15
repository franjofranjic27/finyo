package ch.finyo.integration.openfigi;

import ch.finyo.config.ResilienceConfig;
import ch.finyo.integration.ResilientCall;
import ch.finyo.marketdata.MarketDataProperties;
import ch.finyo.marketdata.spi.DataSource;
import ch.finyo.common.SourceResult;
import ch.finyo.marketdata.spi.SecurityId;
import ch.finyo.marketdata.spi.SecurityReference;
import ch.finyo.marketdata.spi.SecurityType;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static ch.finyo.common.SourceResults.foundValue;
import static ch.finyo.common.SourceResults.unavailableReason;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for OpenFigiReferenceAdapter.
 *
 * Against a loopback HttpServer, for the same reason as SixReferenceAdapterTest: the
 * response is a list of lists ([{"data":[{...}]}]) and the miss case carries a
 * "warning" instead of "data". Both only hold up if the real deserialisation runs.
 *
 * Fixtures are live OpenFIGI responses (docs/DATENQUELLEN.md).
 *
 * Focus areas:
 *   1. The securityType mapping, whose ORDER is load-bearing: an ETP also declares
 *      securityType2 "Mutual Fund", so testing the ETF case is what keeps every ETF
 *      in the portfolio from being filed as a fund.
 *   2. supports(): ISIN only — a valor would burn a rate-limited request on a
 *      guaranteed miss.
 *   3. The SW listing wins when a security is mapped to several exchanges.
 *   4. What OpenFIGI does NOT give us: no currency, no valor. The currency in
 *      particular must stay null all the way down — an OpenFIGI-resolved USD ETF
 *      defaulted to CHF is the original bug, rebuilt.
 */
@DisplayName("OpenFigiReferenceAdapter")
class OpenFigiReferenceAdapterTest {

    private static final String ISIN = "IE00B4L5Y983";

    private HttpServer server;
    // volatile: written by the HttpServer's handler thread, read by the test thread
    private volatile String requestedPath;
    private volatile String requestBody;
    private volatile String apiKeyHeader;
    private volatile int requestCount;
    private volatile String responseBody;
    private volatile int responseStatus;

    @BeforeEach
    void startStubServer() throws IOException {
        responseBody = mappingResponse("ETP", "Mutual Fund");
        responseStatus = 200;
        requestCount = 0;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestCount++;
            requestedPath = exchange.getRequestURI().getPath();
            apiKeyHeader = exchange.getRequestHeaders().getFirst("X-OPENFIGI-APIKEY");
            requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
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
    // Fixtures & factory
    // -------------------------------------------------------------------------

    /** The live answer for the iShares Core MSCI World ETF, with the type fields parameterised. */
    private static String mappingResponse(String securityType, String securityType2) {
        return """
                [{"data":[{"figi":"BBG001VJ61Q4",
                           "name":"ISHARES CORE MSCI WORLD",
                           "ticker":"SWDA",
                           "exchCode":"SW",
                           "securityType":"%s",
                           "securityType2":"%s",
                           "marketSector":"Equity"}]}]
                """.formatted(securityType, securityType2);
    }

    /** A miss: no "data" key at all, just a warning. */
    private static final String NO_IDENTIFIER_FOUND = """
            [{"warning":"No identifier found."}]
            """;

    private OpenFigiReferenceAdapter newAdapter() {
        return newAdapter(null);
    }

    private OpenFigiReferenceAdapter newAdapter(String apiKey) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        MarketDataProperties properties = new MarketDataProperties(
                List.of("openfigi"),
                // OpenFIGI is symbology and has no prices at all — it is in no quote chain.
                List.of(),
                new MarketDataProperties.SixProperties(false, null),
                new MarketDataProperties.OpenFigiProperties(true, baseUrl, apiKey),
                new MarketDataProperties.EodhdProperties(false, null, null));

        ResilienceConfig resilience = new ResilienceConfig();
        ResilientCall resilientCall = new ResilientCall(
                resilience.circuitBreakerRegistry(),
                resilience.retryRegistry(),
                resilience.rateLimiterRegistry(properties));

        // The production bean is Boot's auto-configured builder (it carries the
        // connect/read timeouts); a plain builder is the right stand-in here, where the
        // stub answers immediately.
        return new OpenFigiReferenceAdapter(properties, RestClient.builder(), resilientCall);
    }

    // =========================================================================
    // Provider contract
    // =========================================================================

    @Nested
    @DisplayName("provider contract")
    class ProviderContract {

        @Test
        void is_registered_under_the_configuration_key_openfigi() {
            assertThat(newAdapter().name()).isEqualTo("openfigi");
        }

        @Test
        void supports_an_isin() {
            assertThat(newAdapter().supports(new SecurityId.Isin(ISIN))).isTrue();
        }

        @Test
        void does_not_support_a_valor() {
            // ID_WERTPAPIER is the German WKN, not the Swiss valor — OpenFIGI simply
            // cannot answer these.
            assertThat(newAdapter().supports(new SecurityId.Valor("3886335"))).isFalse();
        }

        @Test
        void never_calls_openfigi_for_an_identifier_it_cannot_resolve() {
            // NotFound, not Unavailable: OpenFIGI is up, it just has no concept of a
            // valor. Reporting an outage here would mark healthy instruments UNRESOLVED
            // and retry them on every import, forever.
            SourceResult<SecurityReference> result = newAdapter().lookup(new SecurityId.Valor("3886335"));

            assertThat(result).isEqualTo(SourceResult.notFound());
            assertThat(requestCount).isZero();
        }
    }

    // =========================================================================
    // Request building
    // =========================================================================

    @Nested
    @DisplayName("request building")
    class RequestBuilding {

        @Test
        void posts_the_isin_to_the_v3_mapping_endpoint() {
            newAdapter().lookup(new SecurityId.Isin(ISIN));

            assertThat(requestedPath).isEqualTo("/v3/mapping");
            assertThat(requestBody)
                    .contains("\"idType\":\"ID_ISIN\"")
                    .contains("\"idValue\":\"" + ISIN + "\"");
        }

        @Test
        void sends_the_api_key_when_one_is_configured() {
            // The key is optional and only buys throughput (25 requests per 6 s instead of
            // per minute) — but if it is configured and not sent, a bulk import runs into
            // the low limit for no reason at all.
            newAdapter("secret-key").lookup(new SecurityId.Isin(ISIN));

            assertThat(apiKeyHeader).isEqualTo("secret-key");
        }

        @Test
        void works_without_an_api_key() {
            newAdapter(null).lookup(new SecurityId.Isin(ISIN));

            assertThat(apiKeyHeader).isNull();
            assertThat(requestCount).isEqualTo(1);
        }
    }

    // =========================================================================
    // Response mapping
    // =========================================================================

    @Nested
    @DisplayName("response mapping")
    class Mapping {

        @Test
        void maps_a_mapping_hit_onto_a_security_reference() {
            SecurityReference reference = foundValue(newAdapter().lookup(new SecurityId.Isin(ISIN)));

            assertThat(reference.isin()).isEqualTo(ISIN);
            assertThat(reference.ticker()).isEqualTo("SWDA");
            assertThat(reference.name()).isEqualTo("ISHARES CORE MSCI WORLD");
            assertThat(reference.source()).isEqualTo(DataSource.OPENFIGI);
            assertThat(reference.retrievedAt()).isNotNull();
        }

        @Test
        void maps_an_ETP_to_an_ETF_even_though_it_also_declares_itself_a_mutual_fund() {
            // This is the trap the mapping order exists for. OpenFIGI answers
            // securityType "ETP" AND securityType2 "Mutual Fund" for every ETF; check the
            // fund rule first and the entire portfolio is misclassified.
            SecurityReference reference = foundValue(newAdapter().lookup(new SecurityId.Isin(ISIN)));

            assertThat(reference.type()).isEqualTo(SecurityType.ETF);
        }

        @ParameterizedTest(name = "securityType {0} maps to {1}")
        @CsvSource({
                "ETP,            ETF",
                "Open-End Fund,  FUND",
                "Common Stock,   EQUITY",
                "Corp Bond,      BOND",
                "Warrant,        OTHER"
        })
        void maps_the_security_type(String securityType, SecurityType expected) {
            responseBody = mappingResponse(securityType, "Mutual Fund");

            SecurityReference reference = foundValue(newAdapter().lookup(new SecurityId.Isin(ISIN)));

            assertThat(reference.type()).isEqualTo(expected);
        }

        @Test
        void carries_no_currency_and_no_valor() {
            // OpenFIGI is symbology, not market data. Inventing a currency here — CHF is
            // the tempting one — would reintroduce exactly the bug this redesign removes:
            // a USD ETF resolved through OpenFIGI (which is precisely what happens when
            // SIX does not know it or is down) would be summed into the portfolio total as
            // francs. Null means unknown, and unknown must stay distinguishable from CHF.
            SecurityReference reference = foundValue(newAdapter().lookup(new SecurityId.Isin(ISIN)));

            assertThat(reference.currency()).isNull();
            assertThat(reference.valor()).isNull();
        }

        @Test
        void prefers_the_swiss_listing_when_a_security_is_mapped_to_several_exchanges() {
            // finyo is a Swiss product: of the dozen listings of an MSCI World ETF, the
            // SIX one is the one the user holds.
            responseBody = """
                    [{"data":[{"figi":"BBG00GG4LNW6","name":"ISHARES CORE MSCI WORLD","ticker":"IWDA",
                               "exchCode":"NA","securityType":"ETP","securityType2":"Mutual Fund","marketSector":"Equity"},
                              {"figi":"BBG001VJ61Q4","name":"ISHARES CORE MSCI WORLD","ticker":"SWDA",
                               "exchCode":"SW","securityType":"ETP","securityType2":"Mutual Fund","marketSector":"Equity"},
                              {"figi":"BBG00BF0T8P6","name":"ISHARES CORE MSCI WORLD","ticker":"IWDA",
                               "exchCode":"LN","securityType":"ETP","securityType2":"Mutual Fund","marketSector":"Equity"}]}]
                    """;

            SecurityReference reference = foundValue(newAdapter().lookup(new SecurityId.Isin(ISIN)));

            assertThat(reference.ticker()).isEqualTo("SWDA");
        }

        @Test
        void takes_the_first_listing_when_none_of_them_is_swiss() {
            responseBody = """
                    [{"data":[{"figi":"BBG00GG4LNW6","name":"ISHARES CORE MSCI WORLD","ticker":"IWDA",
                               "exchCode":"NA","securityType":"ETP","securityType2":"Mutual Fund","marketSector":"Equity"},
                              {"figi":"BBG00BF0T8P6","name":"ISHARES CORE MSCI WORLD","ticker":"IWDL",
                               "exchCode":"LN","securityType":"ETP","securityType2":"Mutual Fund","marketSector":"Equity"}]}]
                    """;

            SecurityReference reference = foundValue(newAdapter().lookup(new SecurityId.Isin(ISIN)));

            assertThat(reference.ticker()).isEqualTo("IWDA");
        }

        @Test
        void maps_a_record_without_a_security_type_to_OTHER() {
            // Not every FIGI record is typed. OTHER is the signal for the investment module
            // to fall back to its name heuristic rather than assert a wrong type.
            responseBody = """
                    [{"data":[{"figi":"BBG001VJ61Q4","name":"SOMETHING","ticker":"XYZ",
                               "exchCode":"SW","securityType":null,"securityType2":null,"marketSector":null}]}]
                    """;

            SecurityReference reference = foundValue(newAdapter().lookup(new SecurityId.Isin(ISIN)));

            assertThat(reference.type()).isEqualTo(SecurityType.OTHER);
        }
    }

    // =========================================================================
    // NotFound is about the security; Unavailable is about OpenFIGI
    // =========================================================================

    @Nested
    @DisplayName("distinguishes an unknown security from an unreachable OpenFIGI")
    class NotFoundVersusUnavailable {

        @Test
        void reports_NotFound_when_openfigi_knows_no_such_identifier() {
            // The miss carries "warning" and no "data" at all — an expected shape, not a
            // parse failure, and a durable answer about the security.
            responseBody = NO_IDENTIFIER_FOUND;

            SourceResult<SecurityReference> result = newAdapter().lookup(new SecurityId.Isin("CH9999999999"));

            assertThat(result).isEqualTo(SourceResult.notFound());
        }

        @Test
        void reports_NotFound_when_the_result_carries_an_empty_data_array() {
            responseBody = "[{\"data\":[]}]";

            SourceResult<SecurityReference> result = newAdapter().lookup(new SecurityId.Isin(ISIN));

            assertThat(result).isEqualTo(SourceResult.notFound());
        }

        @Test
        void reports_Unavailable_when_openfigi_answers_with_a_server_error() {
            responseStatus = 500;
            responseBody = "{\"error\":\"boom\"}";

            SourceResult<SecurityReference> result = newAdapter().lookup(new SecurityId.Isin(ISIN));

            assertThat(unavailableReason(result)).startsWith("openfigi:");
        }

        @Test
        void reports_Unavailable_when_the_rate_limit_is_hit() {
            // OpenFIGI answers 429 once the published limit is exceeded. That is the vendor
            // refusing to talk, not a statement about the security — an instrument imported
            // at that moment must be marked UNRESOLVED and retried, not frozen as a guess.
            responseStatus = 429;
            responseBody = "{\"error\":\"Too Many Requests\"}";

            SourceResult<SecurityReference> result = newAdapter().lookup(new SecurityId.Isin(ISIN));

            assertThat(unavailableReason(result)).startsWith("openfigi:");
        }

        @Test
        void reports_Unavailable_when_openfigi_answers_with_something_that_is_not_json() {
            responseBody = "<html>we moved</html>";

            SourceResult<SecurityReference> result = newAdapter().lookup(new SecurityId.Isin(ISIN));

            assertThat(unavailableReason(result)).startsWith("openfigi:");
        }
    }
}
