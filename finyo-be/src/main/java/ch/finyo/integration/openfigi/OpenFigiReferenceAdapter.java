package ch.finyo.integration.openfigi;

import ch.finyo.config.ResilienceConfig;
import ch.finyo.integration.CallOutcome;
import ch.finyo.integration.ResilientCall;
import ch.finyo.marketdata.MarketDataProperties;
import ch.finyo.marketdata.spi.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Master-data lookup against OpenFIGI (Bloomberg), the licensing-clean source.
 *
 * Where SIX is tolerated, OpenFIGI is <em>granted</em>: the FIGI symbology is
 * published under an MIT/Open-Data licence that explicitly permits storing and
 * redistributing the data. That makes it the provider that survives finyo becoming
 * multi-user, and the reason it sits in the chain behind SIX rather than not at all.
 *
 * It earns its place a second way: OpenFIGI resolves the unlisted CSIF funds behind
 * VIAC and finpension, which SIX answers with {@code totalRows: 0} because they are
 * institutional share classes with no exchange listing (both verified live).
 *
 * Two honest limitations:
 * <ul>
 *   <li><b>No currency.</b> OpenFIGI is symbology, not market data. An instrument
 *       resolved only through OpenFIGI keeps the column default (CHF), which is a
 *       guess — {@code Instrument.source} records that so it can be surfaced.</li>
 *   <li><b>No valor numbers.</b> {@code ID_WERTPAPIER} is the German WKN, not the
 *       Swiss valor — hence {@link #supports} rejects them instead of burning a
 *       rate-limited request on a guaranteed miss.</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "finyo.marketdata.openfigi", name = "enabled", havingValue = "true")
public class OpenFigiReferenceAdapter implements SecurityReferenceProvider {

    static final String NAME = "openfigi";

    /** finyo is a Swiss product: when a security trades in several places, the SIX line wins. */
    private static final String PREFERRED_EXCHANGE = "SW";

    private static final ParameterizedTypeReference<List<OpenFigiPayloads.MappingResult>> RESULT_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final ResilientCall resilientCall;

    // Boot's auto-configured builder, not RestClient.builder(): only the bean carries
    // spring.http.client.{connect,read}-timeout. See application.yaml.
    public OpenFigiReferenceAdapter(MarketDataProperties properties,
                                    RestClient.Builder restClientBuilder,
                                    ResilientCall resilientCall) {
        this.resilientCall = resilientCall;

        RestClient.Builder builder = restClientBuilder
                .baseUrl(properties.openfigi().baseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        // The key is optional: without it OpenFIGI allows 25 requests/minute, with it
        // 25 per 6 seconds. Both work — the key only buys throughput.
        String apiKey = properties.openfigi().apiKey();
        if (StringUtils.hasText(apiKey)) {
            builder.defaultHeader("X-OPENFIGI-APIKEY", apiKey);
        } else {
            log.info("OpenFIGI running without an API key — reduced rate limit (25 requests/minute)");
        }
        this.restClient = builder.build();
    }

    @Override
    public String name() {
        return NAME;
    }

    /** ISIN only. OpenFIGI has no Swiss valor, and a ticker without an exchange is ambiguous. */
    @Override
    public boolean supports(SecurityId id) {
        return id instanceof SecurityId.Isin;
    }

    @Override
    public LookupResult lookup(SecurityId id) {
        if (!supports(id)) {
            return LookupResult.notFound();
        }

        CallOutcome<Optional<SecurityReference>> outcome =
                resilientCall.execute(ResilienceConfig.OPENFIGI, () -> fetchAndMap(id.value()));

        return switch (outcome) {
            case CallOutcome.Success<Optional<SecurityReference>>(var reference) ->
                    reference.map(LookupResult::found).orElseGet(LookupResult::notFound);
            case CallOutcome.Unavailable<Optional<SecurityReference>>(var reason) ->
                    LookupResult.unavailable("openfigi: " + reason);
        };
    }

    private Optional<SecurityReference> fetchAndMap(String isin) {
        log.debug("OpenFIGI mapping lookup for isin={}", isin);
        List<OpenFigiPayloads.MappingResult> results = restClient.post()
                .uri("/v3/mapping")
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(new OpenFigiPayloads.MappingRequest("ID_ISIN", isin)))
                .retrieve()
                .body(RESULT_TYPE);

        // A miss carries {"warning": "No identifier found."} instead of data — an answer,
        // not a failure.
        return results == null
                ? Optional.empty()
                : firstRecord(results).map(figi -> toReference(figi, isin));
    }

    /** One security often maps to several listings; prefer the SIX one, else take what we get. */
    private static Optional<OpenFigiPayloads.FigiRecord> firstRecord(List<OpenFigiPayloads.MappingResult> results) {
        return results.stream()
                .map(OpenFigiPayloads.MappingResult::data)
                .filter(data -> data != null && !data.isEmpty())
                .findFirst()
                .flatMap(data -> data.stream()
                        .min(Comparator.comparing(figi -> PREFERRED_EXCHANGE.equals(figi.exchCode()) ? 0 : 1)));
    }

    private SecurityReference toReference(OpenFigiPayloads.FigiRecord figi, String isin) {
        return new SecurityReference(
                isin,
                null,   // OpenFIGI has no Swiss valor
                figi.ticker(),
                figi.name(),
                toSecurityType(figi),
                // Null, and it stays null all the way into the database. The tempting move
                // is to default it to CHF — and that would rebuild the exact bug this module
                // exists to kill: a USD ETF resolved through OpenFIGI (which is precisely the
                // case when SIX does not know it or is down) would be stored as CHF, stamped
                // with a source that looks authoritative, and summed into the portfolio total
                // as francs. "Unknown" and "CHF" must stay distinguishable.
                null,
                null,
                DataSource.OPENFIGI,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    /**
     * OpenFIGI's taxonomy, mapped from live responses rather than documentation:
     * an ETF is {@code securityType: "ETP"}, the CSIF funds are
     * {@code "Open-End Fund"}, shares are {@code "Common Stock"}. Order matters —
     * an ETP also carries {@code securityType2: "Mutual Fund"}, so the ETF test
     * has to come before the fund test or every ETF would be filed as a fund.
     */
    private static SecurityType toSecurityType(OpenFigiPayloads.FigiRecord figi) {
        String type = figi.securityType() == null
                ? "" : figi.securityType().toLowerCase(Locale.ROOT);

        if (type.contains("etp")) {
            return SecurityType.ETF;
        }
        if (type.contains("fund")) {
            return SecurityType.FUND;
        }
        if (type.contains("stock") || type.contains("equity") || type.contains("share")) {
            return SecurityType.EQUITY;
        }
        if (type.contains("bond") || type.contains("note")) {
            return SecurityType.BOND;
        }
        return SecurityType.OTHER;
    }
}
