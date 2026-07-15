package ch.finyo.integration.six;

import ch.finyo.common.SourceResult;
import ch.finyo.common.money.CurrencyCode;
import ch.finyo.config.ResilienceConfig;
import ch.finyo.integration.CallOutcome;
import ch.finyo.integration.ResilientCall;
import ch.finyo.marketdata.MarketDataProperties;
import ch.finyo.marketdata.spi.*;
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
import java.util.Map;
import java.util.Optional;

/**
 * Quotes from the SIX FQS endpoint.
 *
 * <pre>
 * GET /fqs/movie.json?select=ISIN,ClosingPrice,Currency,LatestTradeDate&amp;where=ISIN=IE00B4L5Y983
 * → ["IE00B4L5Y983", 144.2, "USD", 20260714]
 * </pre>
 *
 * This is the endpoint that actually exists. Its predecessor, {@code SixMarketDataClient},
 * called {@code /instruments/{id}/eod-closing-prices/latest} — a path that was guessed from
 * SIX's marketing pages, that never returned anything, and whose failure was swallowed so
 * quietly that the portfolio simply showed the purchase price instead.
 *
 * The feed is delayed by 15 minutes ({@code delayMinutes: 15} in every response), so the
 * quote is marked {@code delayed} and the UI says so rather than implying a live price.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "finyo.marketdata.six", name = "enabled", havingValue = "true")
public class SixQuoteAdapter implements QuoteProvider {

    private static final String SELECT = "ISIN,ClosingPrice,Currency,LatestTradeDate";
    private static final DateTimeFormatter FQS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RestClient restClient;
    private final ResilientCall resilientCall;
    private final String baseUrl;

    public SixQuoteAdapter(MarketDataProperties properties,
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
    public SourceResult<Quote> quote(SecurityId id) {
        CallOutcome<Optional<Quote>> outcome =
                resilientCall.execute(ResilienceConfig.SIX, () -> fetchAndMap(SixQuery.filterFor(id)));

        return switch (outcome) {
            case CallOutcome.Success<Optional<Quote>>(var quote) ->
                    quote.<SourceResult<Quote>>map(SourceResult::found).orElseGet(SourceResult::notFound);
            case CallOutcome.Unavailable<Optional<Quote>>(var reason) ->
                    SourceResult.unavailable("six: " + reason);
        };
    }

    private Optional<Quote> fetchAndMap(String filter) {
        URI uri = URI.create("%s/movie.json?select=%s&where=%s".formatted(baseUrl, SELECT, filter));
        log.debug("SIX quote lookup: {}", uri);
        FqsResponse response = restClient.get().uri(uri).retrieve().body(FqsResponse.class);

        // totalRows: 0 means the security is not listed on SIX — a real answer, not a failure.
        return response == null ? Optional.empty() : response.firstRow().flatMap(SixQuoteAdapter::toQuote);
    }

    /**
     * A row without a closing price is a row we cannot use — an instrument that is listed but
     * has never traded, for instance. Reported as "no quote" rather than as a zero, because a
     * price of zero would flow straight into the portfolio total as a real number.
     */
    private static Optional<Quote> toQuote(Map<String, Object> row) {
        BigDecimal price = decimal(row.get("ClosingPrice"));
        String isin = SixQuery.text(row.get("ISIN"));
        // A zero or negative close is not a price — SIX returns 0 for a listed instrument that
        // has never traded. It has to be caught here and not only for null, because a literal 0
        // parses cleanly to BigDecimal.ZERO, sails past a null check, and lands in the portfolio
        // as a value of 0 and a −100% return that looks entirely real. That is exactly the
        // "fresh-looking wrong number" this module exists to prevent.
        if (price == null || price.signum() <= 0 || isin == null) {
            return Optional.empty();
        }
        return Optional.of(new Quote(
                isin,
                price,
                CurrencyCode.ofNullable(SixQuery.text(row.get("Currency"))),
                tradeDate(row.get("LatestTradeDate")),
                OffsetDateTime.now(ZoneOffset.UTC),
                true,   // the free FQS feed is always 15 minutes behind
                DataSource.SIX));
    }

    /** FQS sends the trading day as the integer 20260714. */
    private static LocalDate tradeDate(Object raw) {
        String text = SixQuery.text(raw);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text, FQS_DATE);
        } catch (RuntimeException e) {
            log.warn("SIX returned an unreadable trade date: {}", text);
            return null;
        }
    }

    private static BigDecimal decimal(Object raw) {
        String text = SixQuery.text(raw);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
