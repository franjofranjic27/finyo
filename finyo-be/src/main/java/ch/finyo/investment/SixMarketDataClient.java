package ch.finyo.investment;

import ch.finyo.config.CacheConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SixMarketDataClient {

    private final RestClient restClient;
    private final SixMarketDataProperties properties;

    public SixMarketDataClient(SixMarketDataProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }

    /**
     * The identifier is user input (ISIN/valor from a position request) and is spliced
     * into a URL path that carries a Bearer token. A "../" would therefore send that
     * token to a different path on the SIX host. Whitelisting rather than escaping:
     * every legitimate ISIN, valor and ticker is alphanumeric.
     *
     * The same guard lives in SixQuery for the new adapter. This class is deleted in
     * PR 2 — until then it runs on every bulk-import row, so it does not get to wait.
     */
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z0-9]{1,20}$");

    @Cacheable(value = CacheConfig.CACHE_MARKET_DATA, key = "#identifier")
    public Optional<MarketDataResponse> fetchByValorOrIsin(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        if (!SAFE_IDENTIFIER.matcher(identifier).matches()) {
            log.warn("Refusing SIX lookup for an identifier that is not alphanumeric");
            return Optional.empty();
        }

        log.debug("Fetching market data from SIX for identifier: {}", identifier);

        try {
            // SIX Financial Information REST API
            // Docs: https://www.six-group.com/en/products-services/financial-information.html
            // The actual endpoint path depends on your SIX subscription tier.
            // This implementation targets the SIX Financial Data REST API v1.
            // Endpoint: GET /instruments/{valor}/eod-closing-prices/latest
            // For lookup by ISIN: GET /instruments?isin={isin}&fields=valor,isin,name,currency,latestPrice

            String path = "/instruments/" + identifier + "/eod-closing-prices/latest";

            @SuppressWarnings("rawtypes")
            Map response = restClient.get()
                    .uri(path)
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                return Optional.empty();
            }

            return Optional.of(mapSixResponse(response));

        } catch (RestClientException e) {
            log.warn("SIX API call failed for identifier {}: {}", identifier, e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("rawtypes")
    private MarketDataResponse mapSixResponse(Map raw) {
        // Map the SIX API response to our MarketDataResponse.
        // SIX returns fields like: valor, isin, name, currency, lastPrice, etc.
        // Field names may vary by subscription. This mapping uses common field names.

        Object lastPriceObj = raw.getOrDefault("lastPrice", raw.get("close"));
        BigDecimal lastPrice = lastPriceObj != null
                ? new BigDecimal(lastPriceObj.toString()) : null;

        Object changeObj = raw.getOrDefault("changePercent", raw.get("performancePercent1Day"));
        Double changePct = changeObj != null
                ? Double.parseDouble(changeObj.toString()) : null;

        return new MarketDataResponse(
                raw.getOrDefault("valor", "").toString(),
                raw.getOrDefault("isin", "").toString(),
                raw.getOrDefault("ticker", "").toString(),
                raw.getOrDefault("name", "").toString(),
                raw.getOrDefault("exchange", "SIX").toString(),
                raw.getOrDefault("currency", "CHF").toString(),
                lastPrice,
                null, // change1d — absolute CHF change not provided by this endpoint
                changePct,
                safeDecimal(raw.get("performancePercent1Week")),
                safeDecimal(raw.get("performancePercent1Month")),
                safeDecimal(raw.get("performancePercentYtd")),
                safeDecimal(raw.get("performancePercent1Year")),
                safeDecimal(raw.get("high52w")),
                safeDecimal(raw.get("low52w")),
                safeDecimal(raw.get("marketCap")),
                safeDecimal(raw.get("dividendYield")),
                raw.getOrDefault("instrumentType", "").toString(),
                raw.getOrDefault("sector", "").toString(),
                safeDecimal(raw.get("ter")),
                OffsetDateTime.now(ZoneOffset.UTC),
                false
        );
    }

    private BigDecimal safeDecimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException _) {
            return null;
        }
    }
}
