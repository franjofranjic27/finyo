package ch.finyo.integration.six;

import ch.finyo.common.money.CurrencyCode;
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
 * Unit tests for SixReferenceAdapter.
 *
 * The adapter builds its RestClient from an injected builder, so the tests run against a
 * throwaway JDK HttpServer on an ephemeral loopback port. Nothing leaves the machine.
 * That is deliberate rather than a compromise: FQS answers column-oriented
 * ("rowData": [[...]]), the valor arrives as a JSON number, and both quirks only show up
 * if the real deserialisation runs. A hand-built FqsResponse would test the mapper
 * against my assumption of the wire format instead of the format itself.
 *
 * All fixtures are the responses SIX actually returns (docs/DATENQUELLEN.md).
 *
 * Focus areas:
 *   1. Column-oriented mapping, including the numeric valor and the currency.
 *   2. ProductLine to SecurityType, and OTHER as the signal for "ask the heuristic".
 *   3. The distinction the module exists for: totalRows: 0 is NotFound (a fact about the
 *      security), a 500 or a timeout is Unavailable (a fact about SIX). Only the former
 *      lets the caller write a guess down as final.
 */
@DisplayName("SixReferenceAdapter")
class SixReferenceAdapterTest {

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
        responseBody = nestleResponse("BC");
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
    // Fixtures & factory
    // -------------------------------------------------------------------------

    /** The live FQS answer for Nestlé — note ValorNumber is a JSON number, not a string. */
    private static String nestleResponse(String productLine) {
        return """
                {"totalRows":1,
                 "colNames":["ISIN","ValorNumber","ValorSymbol","ShortName","ProductLine","IssuerNameFull","TradingBaseCurrency"],
                 "rowData":[["CH0038863350",3886335,"NESN","NESTLE N","%s","Nestlé SA","CHF"]]}
                """.formatted(productLine);
    }

    /** What FQS answers for a security it does not list — an unlisted CSIF 3a fund, for instance. */
    private static final String NOT_LISTED = """
            {"totalRows":0,
             "colNames":["ISIN","ValorNumber","ValorSymbol","ShortName","ProductLine","IssuerNameFull","TradingBaseCurrency"],
             "rowData":[]}
            """;

    private SixReferenceAdapter newAdapter() {
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

        // The production bean is Boot's auto-configured builder (it carries the
        // connect/read timeouts); a plain builder is the right stand-in here, where the
        // stub answers immediately.
        return new SixReferenceAdapter(properties, RestClient.builder(), resilientCall);
    }

    // =========================================================================
    // Provider contract
    // =========================================================================

    @Test
    void is_registered_under_the_configuration_key_six() {
        assertThat(newAdapter().name()).isEqualTo("six");
    }

    @Test
    void supports_both_identifier_kinds() {
        // SIX is the only free source that resolves a Swiss valor number — that is
        // the reason it leads the chain.
        SixReferenceAdapter adapter = newAdapter();

        assertThat(adapter.supports(new SecurityId.Isin(ISIN))).isTrue();
        assertThat(adapter.supports(new SecurityId.Valor("3886335"))).isTrue();
    }

    // =========================================================================
    // Response mapping
    // =========================================================================

    @Nested
    @DisplayName("maps the column-oriented FQS payload")
    class Mapping {

        @Test
        void maps_the_column_oriented_response_onto_a_security_reference() {
            SecurityReference reference = foundValue(newAdapter().lookup(new SecurityId.Isin(ISIN)));

            assertThat(reference.isin()).isEqualTo(ISIN);
            assertThat(reference.ticker()).isEqualTo("NESN");
            assertThat(reference.name()).isEqualTo("NESTLE N");
            assertThat(reference.issuer()).isEqualTo("Nestlé SA");
            assertThat(reference.type()).isEqualTo(SecurityType.EQUITY);
            assertThat(reference.currency()).isEqualTo(CurrencyCode.CHF);
            assertThat(reference.retrievedAt()).isNotNull();
        }

        @Test
        void maps_the_numeric_valor_cell_to_a_string() {
            // FQS sends 3886335 as a JSON number; the domain keeps valors as text, and a
            // valor that arrives as an Integer would fail the moment it hits the column.
            SecurityReference reference = foundValue(newAdapter().lookup(new SecurityId.Isin(ISIN)));

            assertThat(reference.valor()).isEqualTo("3886335");
        }

        @Test
        void stamps_the_reference_with_SIX_as_its_source() {
            // Provenance is the point of the whole redesign: a value must always say
            // where it came from.
            SecurityReference reference = foundValue(newAdapter().lookup(new SecurityId.Isin(ISIN)));

            assertThat(reference.source()).isEqualTo(DataSource.SIX);
        }

        @ParameterizedTest(name = "ProductLine {0} maps to {1}")
        @CsvSource({
                "BC, EQUITY",
                "ET, ETF",
                "PF, FUND",
                "ZZ, OTHER"     // anything unknown: OTHER, which tells the investment module to fall back to its heuristic
        })
        void maps_the_product_line_to_a_security_type(String productLine, SecurityType expected) {
            responseBody = nestleResponse(productLine);

            SecurityReference reference = foundValue(newAdapter().lookup(new SecurityId.Isin(ISIN)));

            assertThat(reference.type()).isEqualTo(expected);
        }

        @Test
        void maps_a_row_without_a_product_line_to_OTHER() {
            // FQS does not guarantee every column is filled. A missing ProductLine must
            // become OTHER — the signal to fall back to the name heuristic — rather than
            // an NPE inside the switch.
            responseBody = """
                    {"totalRows":1,
                     "colNames":["ISIN","ValorNumber","ValorSymbol","ShortName","ProductLine","IssuerNameFull","TradingBaseCurrency"],
                     "rowData":[["CH0038863350",3886335,"NESN","NESTLE N",null,"Nestlé SA","CHF"]]}
                    """;

            SecurityReference reference = foundValue(newAdapter().lookup(new SecurityId.Isin(ISIN)));

            assertThat(reference.type()).isEqualTo(SecurityType.OTHER);
        }

        @Test
        void maps_a_row_without_a_currency_to_a_reference_without_one() {
            // Not every FQS row carries every column; an absent currency must stay absent
            // rather than becoming an empty CurrencyCode (which would not construct) or,
            // worse, a silent CHF.
            responseBody = """
                    {"totalRows":1,
                     "colNames":["ISIN","ValorNumber","ValorSymbol","ShortName","ProductLine","IssuerNameFull","TradingBaseCurrency"],
                     "rowData":[["CH0038863350",3886335,"NESN","NESTLE N","BC",null,null]]}
                    """;

            SecurityReference reference = foundValue(newAdapter().lookup(new SecurityId.Isin(ISIN)));

            assertThat(reference.currency()).isNull();
            assertThat(reference.issuer()).isNull();
            assertThat(reference.isin()).isEqualTo(ISIN);
        }
    }

    // =========================================================================
    // Request building
    // =========================================================================

    @Nested
    @DisplayName("builds the FQS request")
    class RequestBuilding {

        @Test
        void asks_FQS_for_the_reference_columns_filtered_by_isin() {
            newAdapter().lookup(new SecurityId.Isin(ISIN));

            assertThat(requestedPath).isEqualTo("/ref.json");
            assertThat(requestedQuery)
                    .contains("where=ISIN=" + ISIN)
                    .contains("ISIN,ValorNumber,ValorSymbol,ShortName,ProductLine,IssuerNameFull,TradingBaseCurrency");
            // One lookup is one request: a successful call must not be retried.
            assertThat(requestCount).isEqualTo(1);
        }

        @Test
        void filters_by_the_valor_column_when_asked_for_a_valor() {
            newAdapter().lookup(new SecurityId.Valor("3886335"));

            assertThat(requestedQuery).contains("where=ValorNumber=3886335");
        }
    }

    // =========================================================================
    // NotFound is about the security; Unavailable is about SIX
    // =========================================================================

    @Nested
    @DisplayName("distinguishes an unknown security from an unreachable SIX")
    class NotFoundVersusUnavailable {

        @Test
        void reports_NotFound_when_the_security_is_not_listed_on_six() {
            // totalRows: 0 is the answer for the unlisted CSIF funds behind VIAC and
            // finpension. It is a normal, durable outcome — the chain moves on to OpenFIGI,
            // and if nobody knows it either, the name heuristic is legitimately final.
            responseBody = NOT_LISTED;

            SourceResult<SecurityReference> result = newAdapter().lookup(new SecurityId.Isin("CH0214967314"));

            assertThat(result).isEqualTo(SourceResult.notFound());
        }

        @Test
        void reports_Unavailable_when_FQS_answers_with_a_server_error() {
            // Says nothing about the security. Reporting it as NotFound would let the
            // caller freeze a name-derived guess into the database for good.
            responseStatus = 500;
            responseBody = "{\"error\":\"boom\"}";

            SourceResult<SecurityReference> result = newAdapter().lookup(new SecurityId.Isin(ISIN));

            assertThat(unavailableReason(result)).startsWith("six:");
        }

        @Test
        void reports_Unavailable_when_FQS_answers_with_something_that_is_not_json_at_all() {
            // A payload we can no longer parse means SIX changed its wire format — a vendor
            // failure, and it must trip the circuit breaker like any other rather than
            // masquerade as "this security does not exist".
            responseBody = "<html><body>503 Service Unavailable</body></html>";

            SourceResult<SecurityReference> result = newAdapter().lookup(new SecurityId.Isin(ISIN));

            assertThat(unavailableReason(result)).startsWith("six:");
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
                "an empty object                 | {}",
                "no rowData at all               | {\"totalRows\":1,\"colNames\":[\"ISIN\"]}",
                "rows announced but none present | {\"totalRows\":1,\"colNames\":[\"ISIN\"],\"rowData\":[]}"
        })
        void reports_NotFound_for_a_payload_that_parses_but_carries_no_usable_row(String description,
                                                                                  String payload) {
            // SIX FQS is unofficial: there is no contract and no stability promise, so a
            // shape we have never seen is a question of when, not if. These all deserialise
            // cleanly and genuinely hold no row, which is indistinguishable from "not listed"
            // at this level — so they degrade to NotFound and the chain asks the next
            // provider. (A payload that does not parse at all is a different matter: see
            // the test above, which reports Unavailable.)
            responseBody = payload;

            SourceResult<SecurityReference> result = newAdapter().lookup(new SecurityId.Isin(ISIN));

            assertThat(result).as(description).isEqualTo(SourceResult.notFound());
        }

        @Test
        void reports_Unavailable_when_SIX_announces_rows_but_sends_no_column_names() {
            // The dangerous shape, and the reason it gets its own test. FQS is
            // column-oriented: without colNames the rowData cannot be interpreted at all.
            // The payload parses, so it is tempting to treat it as "no result" — but that
            // would mean a SIX format change silently files every instrument as a
            // name-based guess, permanently. It is a vendor failure, and it is reported as
            // one, so the circuit breaker sees it and the instrument stays retryable.
            responseBody = "{\"totalRows\":1,\"rowData\":[[\"CH0038863350\"]]}";

            SourceResult<SecurityReference> result = newAdapter().lookup(new SecurityId.Isin(ISIN));

            assertThat(unavailableReason(result)).startsWith("six:");
        }
    }
}
