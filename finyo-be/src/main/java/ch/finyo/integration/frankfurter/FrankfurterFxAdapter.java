package ch.finyo.integration.frankfurter;

import ch.finyo.common.SourceResult;
import ch.finyo.common.money.CurrencyCode;
import ch.finyo.config.ResilienceConfig;
import ch.finyo.fx.FxProperties;
import ch.finyo.fx.FxRate;
import ch.finyo.fx.FxRateType;
import ch.finyo.fx.spi.FxRateProvider;
import ch.finyo.integration.CallOutcome;
import ch.finyo.integration.ResilientCall;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ECB reference (mid) rates from Frankfurter, self-hosted in compose.
 *
 * <pre>
 * GET /latest-day: /{date}?base=CHF&amp;symbols=EUR      → {"date":"2026-07-14","rates":{"EUR":1.0796}}
 * GET /range:      /{from}..{to}?base=CHF&amp;symbols=EUR → {"rates":{"2025-01-02":{"EUR":1.04}, ...}}
 * </pre>
 *
 * Two things are handled here and nowhere else. The <b>direction</b>: {@code base=CHF} yields
 * EUR-per-CHF, and the domain stores CHF-per-EUR, so every value is inverted before it becomes an
 * {@link FxRate}. And the <b>missing day</b>: a weekend has no rate; the single-day endpoint falls
 * back to the last working day (whose date it returns), and the range endpoint simply omits it —
 * a gap is never interpolated.
 *
 * <p>Range queries use the v1 path on purpose: the v2 range syntax returns HTTP 422.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "finyo.fx.frankfurter", name = "enabled", havingValue = "true")
public class FrankfurterFxAdapter implements FxRateProvider {

    public static final String NAME = "frankfurter";
    private static final String BASE = "CHF";
    /** A rate is not a money amount; eight decimals keeps the inversion faithful. */
    private static final int RATE_SCALE = 8;

    private final RestClient restClient;
    private final ResilientCall resilientCall;
    private final String baseUrl;

    public FrankfurterFxAdapter(FxProperties properties,
                                RestClient.Builder restClientBuilder,
                                ResilientCall resilientCall) {
        this.baseUrl = properties.frankfurter().baseUrl();
        this.resilientCall = resilientCall;
        this.restClient = restClientBuilder
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public FxRateType type() {
        return FxRateType.MID;
    }

    @Override
    public SourceResult<FxRate> rate(CurrencyCode currency, LocalDate on) {
        CallOutcome<Optional<FxRate>> outcome =
                resilientCall.execute(ResilienceConfig.FRANKFURTER, () -> fetchLatest(currency, on));

        return switch (outcome) {
            case CallOutcome.Success<Optional<FxRate>>(var rate) ->
                    rate.<SourceResult<FxRate>>map(SourceResult::found).orElseGet(SourceResult::notFound);
            case CallOutcome.Unavailable<Optional<FxRate>>(var reason) ->
                    SourceResult.unavailable("frankfurter: " + reason);
        };
    }

    @Override
    public List<FxRate> rates(CurrencyCode currency, LocalDate from, LocalDate to) {
        CallOutcome<List<FxRate>> outcome =
                resilientCall.execute(ResilienceConfig.FRANKFURTER, () -> fetchRange(currency, from, to));

        return switch (outcome) {
            case CallOutcome.Success<List<FxRate>>(var rates) -> rates == null ? List.of() : rates;
            case CallOutcome.Unavailable<List<FxRate>>(var reason) -> {
                log.warn("Frankfurter range for {} {}..{} unavailable: {}", currency, from, to, reason);
                yield List.of();
            }
        };
    }

    private Optional<FxRate> fetchLatest(CurrencyCode currency, LocalDate on) {
        URI uri = URI.create("%s/%s?base=%s&symbols=%s".formatted(baseUrl, on, BASE, currency.value()));
        log.debug("Frankfurter rate lookup: {}", uri);
        FrankfurterPayloads.Latest response = restClient.get().uri(uri)
                .retrieve().body(FrankfurterPayloads.Latest.class);
        if (response == null || response.rates() == null || response.date() == null) {
            return Optional.empty();
        }
        return toRate(currency, response.date(), response.rates().get(currency.value()));
    }

    private List<FxRate> fetchRange(CurrencyCode currency, LocalDate from, LocalDate to) {
        URI uri = URI.create("%s/%s..%s?base=%s&symbols=%s".formatted(baseUrl, from, to, BASE, currency.value()));
        log.debug("Frankfurter range lookup: {}", uri);
        FrankfurterPayloads.Range response = restClient.get().uri(uri)
                .retrieve().body(FrankfurterPayloads.Range.class);
        if (response == null || response.rates() == null) {
            return List.of();
        }
        List<FxRate> rates = new ArrayList<>(response.rates().size());
        for (Map.Entry<String, Map<String, BigDecimal>> day : response.rates().entrySet()) {
            BigDecimal unitsPerChf = day.getValue() == null ? null : day.getValue().get(currency.value());
            toRate(currency, LocalDate.parse(day.getKey()), unitsPerChf).ifPresent(rates::add);
        }
        return rates;
    }

    /**
     * Inverts EUR-per-CHF into CHF-per-EUR. A zero, negative or missing rate is dropped rather than
     * inverted — dividing by it would throw or produce a meaningless number that then looked like a
     * real rate in the total.
     */
    private Optional<FxRate> toRate(CurrencyCode currency, LocalDate date, BigDecimal unitsPerChf) {
        if (unitsPerChf == null || unitsPerChf.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal chfPerUnit = BigDecimal.ONE.divide(unitsPerChf, RATE_SCALE, RoundingMode.HALF_UP);
        return Optional.of(FxRate.builder()
                .currency(currency.value())
                .rateDate(date)
                .chfPerUnit(chfPerUnit)
                .rateType(FxRateType.MID)
                .source(NAME)
                .retrievedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
    }
}
