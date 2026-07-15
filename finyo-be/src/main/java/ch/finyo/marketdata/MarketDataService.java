package ch.finyo.marketdata;

import ch.finyo.common.SourceResult;
import ch.finyo.common.SwissTime;
import ch.finyo.marketdata.spi.Quote;
import ch.finyo.marketdata.spi.QuoteProvider;
import ch.finyo.marketdata.spi.SecurityId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The application's only source of prices.
 *
 * <p><b>Reads never touch the network.</b> That is the entire point of this class, and it is
 * the single largest change in this PR. Before, building a portfolio issued one synchronous
 * SIX call per distinct instrument, inside the user's request — so a vendor that accepted the
 * connection and then went quiet would pin a Tomcat thread per position, and a slow SIX made
 * the app slow for everything, including pages that have nothing to do with investments.
 *
 * Prices come from {@code instrument_price} in Postgres. They are put there by
 * {@link PriceSyncJob} overnight and by {@link #refresh} when a position is created, so a new
 * holding is priced immediately rather than waiting for the small hours.
 *
 * <p>The consequence worth stating plainly: a price can be old. That is not hidden. Every
 * {@link PricePoint} carries the day it belongs to and a {@code stale} flag, and the UI shows
 * both. An honest old number beats a fresh-looking wrong one — which is what the old code
 * produced when it silently substituted the purchase price.
 */
@Slf4j
@Service
public class MarketDataService {

    /**
     * A quote may legitimately be a few days old — a Friday close read on a Monday morning is
     * three days behind, and an Easter weekend stretches that further. Beyond four days the
     * data is not merely yesterday's, so it gets flagged.
     */
    private static final int STALE_AFTER_DAYS = 4;

    private final List<QuoteProvider> providers;
    private final InstrumentPriceRepository repository;
    private final InstrumentPriceWriter writer;

    public MarketDataService(List<QuoteProvider> providers,
                             MarketDataProperties properties,
                             InstrumentPriceRepository repository,
                             InstrumentPriceWriter writer) {
        this.repository = repository;
        this.writer = writer;
        this.providers = providers.stream()
                .filter(provider -> properties.quoteProviders().contains(provider.name()))
                .sorted(Comparator.comparingInt(p -> properties.quoteProviders().indexOf(p.name())))
                .toList();
        log.info("Quote provider chain: {}", this.providers.stream().map(QuoteProvider::name).toList());
    }

    /** Latest known price per ISIN, from the database. Never calls a provider. */
    public Map<String, PricePoint> latestPrices(Collection<String> isins) {
        if (isins.isEmpty()) {
            return Map.of();
        }
        LocalDate today = LocalDate.now(SwissTime.ZONE);
        return repository.findLatestForEach(isins).stream()
                .collect(Collectors.toMap(InstrumentPrice::getIsin, price -> toPricePoint(price, today)));
    }

    public Optional<PricePoint> latestPrice(String isin) {
        LocalDate today = LocalDate.now(SwissTime.ZONE);
        return repository.findFirstByIsinOrderByPriceDateDesc(isin)
                .map(price -> toPricePoint(price, today));
    }

    /**
     * Asks the providers for fresh prices and stores what they return. This is the only method
     * that talks to the network, and it is never called from a read.
     *
     * @return how many securities were priced — the rest are either unlisted (no provider has
     *         a price, which is normal for 3a funds) or the provider was unreachable
     */
    public int refresh(Collection<String> isins) {
        int stored = 0;
        for (String isin : isins) {
            if (refreshOne(isin)) {
                stored++;
            }
        }
        return stored;
    }

    private boolean refreshOne(String isin) {
        SecurityId id;
        try {
            id = new SecurityId.Isin(isin);
        } catch (IllegalArgumentException e) {
            log.debug("Skipping price refresh for malformed ISIN {}", isin);
            return false;
        }

        for (QuoteProvider provider : providers) {
            if (!provider.supports(id)) {
                continue;
            }
            switch (quoteQuietly(provider, id)) {
                case SourceResult.Found<Quote>(Quote quote) -> {
                    writer.store(quote);
                    return true;
                }
                case SourceResult.Unavailable<Quote>(String reason) ->
                    // Nothing is written. An outage must not overwrite a good price with a
                    // guess, and it must not be mistaken for "this security has no price".
                        log.warn("Price refresh for {} failed: {}", isin, reason);
                case SourceResult.NotFound<Quote> _ ->
                        log.debug("No provider prices {} — expected for unlisted funds", isin);
            }
        }
        return false;
    }

    private SourceResult<Quote> quoteQuietly(QuoteProvider provider, SecurityId id) {
        try {
            return provider.quote(id);
        } catch (RuntimeException e) {
            return SourceResult.unavailable(provider.name() + ": " + e.getClass().getSimpleName());
        }
    }

    private static PricePoint toPricePoint(InstrumentPrice price, LocalDate today) {
        boolean stale = price.getPriceDate() == null
                || ChronoUnit.DAYS.between(price.getPriceDate(), today) > STALE_AFTER_DAYS;
        return new PricePoint(price.getClose(), price.getCurrency(), price.getPriceDate(),
                price.getSource(), stale);
    }
}
