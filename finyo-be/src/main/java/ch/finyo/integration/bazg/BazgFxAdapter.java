package ch.finyo.integration.bazg;

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
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.List;

/**
 * The official Swiss sell rate, from the Federal Customs Office (BAZG).
 *
 * <pre>
 * GET /rates?d=20260714 → {"date":"14.07.2026","base":"CHF","rates":[{"symbol":"EUR","rate":"0.93661"}]}
 * </pre>
 *
 * CHF-per-unit already, so no inversion. One day per request — there is no range endpoint, so
 * {@link #rates} is empty and history is backfilled from Frankfurter instead. This is a sell rate,
 * higher than the mid rate, and belongs only in tax contexts; it is filed as
 * {@link FxRateType#OFFICIAL_CH} so it can never be picked up for portfolio valuation. See ADR-009.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "finyo.fx.bazg", name = "enabled", havingValue = "true")
public class BazgFxAdapter implements FxRateProvider {

    public static final String NAME = "bazg";
    private static final DateTimeFormatter QUERY_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter RESPONSE_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final RestClient restClient;
    private final ResilientCall resilientCall;
    private final String baseUrl;

    public BazgFxAdapter(FxProperties properties,
                         RestClient.Builder restClientBuilder,
                         ResilientCall resilientCall) {
        this.baseUrl = properties.bazg().baseUrl();
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
        return FxRateType.OFFICIAL_CH;
    }

    @Override
    public SourceResult<FxRate> rate(CurrencyCode currency, LocalDate on) {
        CallOutcome<Optional<FxRate>> outcome =
                resilientCall.execute(ResilienceConfig.BAZG, () -> fetch(currency, on));

        return switch (outcome) {
            case CallOutcome.Success<Optional<FxRate>>(var rate) ->
                    rate.<SourceResult<FxRate>>map(SourceResult::found).orElseGet(SourceResult::notFound);
            case CallOutcome.Unavailable<Optional<FxRate>>(var reason) ->
                    SourceResult.unavailable("bazg: " + reason);
        };
    }

    /** No range endpoint — one request is one day. History comes from Frankfurter. */
    @Override
    public List<FxRate> rates(CurrencyCode currency, LocalDate from, LocalDate to) {
        return List.of();
    }

    private Optional<FxRate> fetch(CurrencyCode currency, LocalDate on) {
        URI uri = URI.create("%s/rates?d=%s".formatted(baseUrl, on.format(QUERY_DATE)));
        log.debug("BAZG rate lookup: {}", uri);
        BazgPayload response = restClient.get().uri(uri).retrieve().body(BazgPayload.class);
        if (response == null || response.rates() == null) {
            return Optional.empty();
        }
        return response.rates().stream()
                .filter(r -> currency.value().equalsIgnoreCase(r.symbol()))
                .findFirst()
                .flatMap(r -> toRate(currency, on, response.date(), r.rate()));
    }

    private Optional<FxRate> toRate(CurrencyCode currency, LocalDate requested, String responseDate, String rawRate) {
        BigDecimal chfPerUnit = decimal(rawRate);
        if (chfPerUnit == null || chfPerUnit.signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(FxRate.builder()
                .currency(currency.value())
                .rateDate(parseDate(responseDate, requested))
                .chfPerUnit(chfPerUnit)
                .rateType(FxRateType.OFFICIAL_CH)
                .source(NAME)
                .retrievedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
    }

    /** The rate belongs to the day BAZG reports; fall back to the requested day if it is unreadable. */
    private static LocalDate parseDate(String responseDate, LocalDate fallback) {
        if (responseDate == null) {
            return fallback;
        }
        try {
            return LocalDate.parse(responseDate, RESPONSE_DATE);
        } catch (RuntimeException e) {
            log.warn("BAZG returned an unreadable date: {}", responseDate);
            return fallback;
        }
    }

    private static BigDecimal decimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
