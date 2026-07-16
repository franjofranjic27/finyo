package ch.finyo.marketdata;

import ch.finyo.common.SourceResult;
import ch.finyo.common.SwissTime;
import ch.finyo.marketdata.spi.PriceBar;
import ch.finyo.marketdata.spi.PriceHistoryProvider;
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

    /** How far back a backfill reaches. Three years is plenty for a chart and keeps the table small. */
    private static final int BACKFILL_YEARS = 3;

    private final List<QuoteProvider> providers;
    private final List<PriceHistoryProvider> historyProviders;
    private final InstrumentPriceRepository repository;
    private final InstrumentPriceWriter writer;

    public MarketDataService(List<QuoteProvider> providers,
                             List<PriceHistoryProvider> historyProviders,
                             MarketDataProperties properties,
                             InstrumentPriceRepository repository,
                             InstrumentPriceWriter writer) {
        this.repository = repository;
        this.writer = writer;
        this.providers = providers.stream()
                .filter(provider -> properties.quoteProviders().contains(provider.name()))
                .sorted(Comparator.comparingInt(p -> properties.quoteProviders().indexOf(p.name())))
                .toList();
        this.historyProviders = historyProviders.stream()
                .filter(provider -> properties.historyProviders().contains(provider.name()))
                .sorted(Comparator.comparingInt(p -> properties.historyProviders().indexOf(p.name())))
                .toList();
        log.info("Quote provider chain: {}", this.providers.stream().map(QuoteProvider::name).toList());
        log.info("History provider chain: {}", this.historyProviders.stream().map(PriceHistoryProvider::name).toList());
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

    /** The stored daily closes for one security, oldest first. Never calls a provider. */
    public List<PricePoint> priceHistory(String isin, LocalDate from) {
        LocalDate today = LocalDate.now(SwissTime.ZONE);
        return repository.findByIsinAndPriceDateGreaterThanEqualOrderByPriceDateAsc(isin, from).stream()
                .map(price -> toPricePoint(price, today))
                .toList();
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
            if (parseIsin(isin).flatMap(this::currentQuote).map(this::storeAndCount).orElse(false)) {
                stored++;
            }
        }
        return stored;
    }

    /**
     * The nightly job's entry point: for each held security, fill in the three-year history the
     * first time it is seen, and just fetch the day's close after that. So a chart works from the
     * first sync a position survives, and every night after is one cheap request per instrument.
     *
     * @return the number of daily closes stored across all securities
     */
    public int refreshOrBackfillHeld(Collection<String> isins) {
        int stored = 0;
        for (String isin : isins) {
            if (repository.countByIsin(isin) < 2) {
                stored += backfill(isin);
            } else if (parseIsin(isin).flatMap(this::currentQuote).map(this::storeAndCount).orElse(false)) {
                stored++;
            }
        }
        return stored;
    }

    /**
     * Fills in a security's daily history so a chart has something to show, going back three
     * years. Idempotent — the underlying upsert makes a re-run a no-op — and network-only, so it
     * belongs to the sync job and to position creation, never to a read.
     *
     * <p>The history feed carries no currency (charts.json is dates and closes only), so the
     * currency is taken from the current quote and applied to every bar. That is correct, not a
     * shortcut: a security trades in one currency across its whole history. If there is no current
     * quote — the security is unlisted, or the provider is down — there is nothing to backfill.
     *
     * @return how many daily closes were stored
     */
    public int backfill(String isin) {
        return parseIsin(isin)
                .flatMap(id -> currentQuote(id).map(quote -> backfill(id, quote)))
                .orElse(0);
    }

    private int backfill(SecurityId id, Quote current) {
        writer.store(current);
        LocalDate from = LocalDate.now(SwissTime.ZONE).minusYears(BACKFILL_YEARS);

        List<PriceBar> bars = historyQuietly(id, from);
        for (PriceBar bar : bars) {
            // Every bar takes the current quote's currency and source — the history feed provides
            // neither, and both are constant across an instrument's history.
            writer.store(new Quote(current.isin(), bar.close(), current.currency(),
                    bar.date(), current.retrievedAt(), current.delayed(), current.source()));
        }
        log.info("Backfilled {} daily closes for {}", bars.size(), current.isin());
        return bars.size() + 1;
    }

    private Optional<SecurityId> parseIsin(String isin) {
        try {
            return Optional.of(new SecurityId.Isin(isin));
        } catch (IllegalArgumentException e) {
            log.debug("Skipping malformed ISIN {}", isin);
            return Optional.empty();
        }
    }

    /** Walks the quote chain; the first provider with an answer wins. */
    private Optional<Quote> currentQuote(SecurityId id) {
        for (QuoteProvider provider : providers) {
            if (!provider.supports(id)) {
                continue;
            }
            switch (quoteQuietly(provider, id)) {
                case SourceResult.Found<Quote>(Quote quote) -> {
                    return Optional.of(quote);
                }
                case SourceResult.Unavailable<Quote>(String reason) ->
                    // Nothing is stored. An outage must not overwrite a good price with a guess,
                    // and it must not be mistaken for "this security has no price".
                        log.warn("Quote for {} unavailable: {}", id.value(), reason);
                case SourceResult.NotFound<Quote> _ ->
                        log.debug("No provider prices {} — expected for unlisted funds", id.value());
            }
        }
        return Optional.empty();
    }

    private boolean storeAndCount(Quote quote) {
        writer.store(quote);
        return true;
    }

    private List<PriceBar> historyQuietly(SecurityId id, LocalDate from) {
        for (PriceHistoryProvider provider : historyProviders) {
            if (!provider.supports(id)) {
                continue;
            }
            SourceResult<List<PriceBar>> result;
            try {
                result = provider.history(id, from);
            } catch (RuntimeException e) {
                log.warn("History for {} failed: {}", id.value(), e.getClass().getSimpleName());
                continue;
            }
            if (result instanceof SourceResult.Found<List<PriceBar>>(List<PriceBar> bars)) {
                return bars;
            }
        }
        return List.of();
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
