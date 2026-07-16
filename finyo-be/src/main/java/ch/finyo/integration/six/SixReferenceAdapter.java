package ch.finyo.integration.six;

import ch.finyo.common.money.CurrencyCode;
import ch.finyo.config.ResilienceConfig;
import ch.finyo.integration.CallOutcome;
import ch.finyo.integration.ResilientCall;
import ch.finyo.marketdata.MarketDataProperties;
import ch.finyo.common.SourceResult;
import ch.finyo.marketdata.spi.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

/**
 * Master-data lookup against the SIX FQS endpoint.
 *
 * <pre>
 * GET /fqs/ref.json?select=ISIN,ValorNumber,ValorSymbol,ShortName,ProductLine,
 *                          IssuerNameFull,TradingBaseCurrency
 *                 &amp;where=ISIN=IE00B4L5Y983
 * </pre>
 *
 * Unauthenticated and free, and the only source that resolves a Swiss valor number.
 * Two things to keep in mind, both verified rather than assumed:
 *
 * <ol>
 *   <li><b>Unofficial.</b> There is no contract and no published stability promise.
 *       Hence the circuit breaker, and hence the persistent cache in front of it.</li>
 *   <li><b>Personal use only.</b> SIX's terms say the data may not be used
 *       commercially. finyo is legally in the clear while it has a single user;
 *       {@code SixPersonalUseCheck} turns that from a footnote into an assertion.</li>
 * </ol>
 *
 * The endpoint SIX is <em>not</em> at: {@code /instruments/{id}/eod-closing-prices/latest},
 * which the previous {@code SixMarketDataClient} guessed and which never existed.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "finyo.marketdata.six", name = "enabled", havingValue = "true")
public class SixReferenceAdapter implements SecurityReferenceProvider {

    static final String NAME = "six";

    private static final String SELECT =
            "ISIN,ValorNumber,ValorSymbol,ShortName,ProductLine,IssuerNameFull,TradingBaseCurrency";

    private final RestClient restClient;
    private final ResilientCall resilientCall;
    private final String baseUrl;

    // Takes Boot's auto-configured builder rather than RestClient.builder(): only the
    // bean carries spring.http.client.{connect,read}-timeout. The static factory would
    // silently produce a client that waits forever — see application.yaml.
    public SixReferenceAdapter(MarketDataProperties properties,
                               RestClient.Builder restClientBuilder,
                               ResilientCall resilientCall) {
        this.baseUrl = properties.six().baseUrl();
        this.resilientCall = resilientCall;
        this.restClient = restClientBuilder
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }

    @Override
    public String name() {
        return NAME;
    }

    /** SIX resolves both identifier kinds — it is the only free source that knows valor numbers. */
    @Override
    public boolean supports(SecurityId id) {
        return true;
    }

    @Override
    public SourceResult<SecurityReference> lookup(SecurityId id) {
        // Parsing happens inside the guarded call on purpose: a payload we can no longer
        // read means SIX changed its wire format, which is a vendor failure and should
        // trip the circuit breaker like any other — not masquerade as "unknown security".
        CallOutcome<Optional<SecurityReference>> outcome =
                resilientCall.execute(ResilienceConfig.SIX, () -> fetchAndMap(SixQuery.filterFor(id)));

        return switch (outcome) {
            case CallOutcome.Success<Optional<SecurityReference>>(var reference) ->
                    reference.map(SourceResult::found).orElseGet(SourceResult::notFound);
            case CallOutcome.Unavailable<Optional<SecurityReference>>(var reason) ->
                    SourceResult.unavailable("six: " + reason);
        };
    }

    private Optional<SecurityReference> fetchAndMap(String filter) {
        URI uri = URI.create("%s/ref.json?select=%s&where=%s".formatted(baseUrl, SELECT, filter));
        log.debug("SIX reference lookup: {}", uri);
        FqsResponse response = restClient.get().uri(uri).retrieve().body(FqsResponse.class);

        // totalRows: 0 is a normal answer, not a failure — it means the security is not
        // listed on SIX. Every unlisted 3a fund answers exactly this.
        return response == null ? Optional.empty() : response.firstRow().map(this::toReference);
    }

    private SecurityReference toReference(Map<String, Object> row) {
        return new SecurityReference(
                SixQuery.text(row.get("ISIN")),
                SixQuery.text(row.get("ValorNumber")),
                SixQuery.text(row.get("ValorSymbol")),
                SixQuery.text(row.get("ShortName")),
                productLineToType(SixQuery.text(row.get("ProductLine"))),
                CurrencyCode.ofNullable(SixQuery.text(row.get("TradingBaseCurrency"))),
                SixQuery.text(row.get("IssuerNameFull")),
                DataSource.SIX,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    /**
     * The delayed FQS reference feed only ever carries these three product lines —
     * probed against the live endpoint, not taken from documentation (there is none).
     * Bonds are absent from this dataset entirely, so there is nothing to map them to.
     * Anything unknown becomes OTHER, which is the signal for the investment module
     * to fall back to its name heuristic rather than assert a wrong type.
     */
    private static SecurityType productLineToType(String productLine) {
        if (productLine == null) {
            return SecurityType.OTHER;
        }
        return switch (productLine) {
            case "BC" -> SecurityType.EQUITY;
            case "ET" -> SecurityType.ETF;
            case "PF" -> SecurityType.FUND;
            default -> SecurityType.OTHER;
        };
    }
}
