package ch.finyo.integration.six;

import ch.finyo.common.SourceResult;
import ch.finyo.config.ResilienceConfig;
import ch.finyo.integration.CallOutcome;
import ch.finyo.integration.ResilientCall;
import ch.finyo.marketdata.MarketDataProperties;
import ch.finyo.marketdata.spi.PriceBar;
import ch.finyo.marketdata.spi.PriceHistoryProvider;
import ch.finyo.marketdata.spi.SecurityId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Daily price history from the SIX FQS charts.json endpoint.
 *
 * <pre>
 * GET /fqs/charts.json?select=ISIN,ClosingPrice&amp;where=ISIN=IE00B4L5Y983&amp;netting=1440&amp;fromdate=20230101
 * </pre>
 *
 * {@code netting=1440} is one bar per day (1440 minutes); without it the feed is intraday. SIX
 * carries daily closes back to 2011.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "finyo.marketdata.six", name = "enabled", havingValue = "true")
public class SixHistoryAdapter implements PriceHistoryProvider {

    private static final String SELECT = "ISIN,ClosingPrice";
    private static final DateTimeFormatter FQS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RestClient restClient;
    private final ResilientCall resilientCall;
    private final String baseUrl;

    public SixHistoryAdapter(MarketDataProperties properties,
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
        return SixReferenceAdapter.NAME;
    }

    @Override
    public boolean supports(SecurityId id) {
        return true;
    }

    @Override
    public SourceResult<List<PriceBar>> history(SecurityId id, LocalDate from) {
        CallOutcome<List<PriceBar>> outcome =
                resilientCall.execute(ResilienceConfig.SIX, () -> fetchAndMap(SixQuery.filterFor(id), from));

        return switch (outcome) {
            case CallOutcome.Success<List<PriceBar>>(var bars) ->
                    bars == null ? SourceResult.notFound() : SourceResult.found(bars);
            case CallOutcome.Unavailable<List<PriceBar>>(var reason) ->
                    SourceResult.unavailable("six: " + reason);
        };
    }

    private List<PriceBar> fetchAndMap(String filter, LocalDate from) {
        URI uri = URI.create("%s/charts.json?select=%s&where=%s&netting=1440&fromdate=%s"
                .formatted(baseUrl, SELECT, filter, from.format(FQS_DATE)));
        log.debug("SIX history lookup: {}", uri);
        FqsChartResponse response = restClient.get().uri(uri).retrieve().body(FqsChartResponse.class);
        return Optional.ofNullable(response).map(FqsChartResponse::toBars).orElse(null);
    }
}
