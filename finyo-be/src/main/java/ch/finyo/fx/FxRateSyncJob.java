package ch.finyo.fx;

import ch.finyo.common.SourceResult;
import ch.finyo.common.SwissTime;
import ch.finyo.common.money.CurrencyCode;
import ch.finyo.fx.spi.FxRateProvider;
import ch.finyo.sync.SyncJob;
import ch.finyo.sync.SyncRun;
import ch.finyo.sync.SyncRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Fetches exchange rates for the currencies users actually hold, once a night.
 *
 * The scope is the distinct non-CHF currencies in {@code instrument}, not a currency table — for a
 * personal tool that is a handful. Each enabled provider is asked for today's rate; a currency with
 * little or no history is backfilled once (Frankfurter serves a whole year in one call, BAZG has no
 * range so it only ever contributes today's official rate).
 *
 * Like {@link ch.finyo.marketdata.PriceSyncJob}, this is the only thing that touches the network —
 * a conversion is a pure database read, so a slow or dead Frankfurter can never reach a user's
 * request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "finyo.sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FxRateSyncJob implements SyncJob {

    public static final String JOB_NAME = "fx-rates";

    /** How far back a currency without history is filled. Three years covers a chart and a P&L. */
    private static final int BACKFILL_YEARS = 3;

    /** Below this many stored rates a currency is treated as new and backfilled. */
    private static final int BACKFILL_THRESHOLD = 2;

    private final List<FxRateProvider> providers;
    private final HeldCurrenciesQuery heldCurrencies;
    private final FxRateRepository repository;
    private final FxRateWriter writer;
    private final SyncRunner syncRunner;

    @Override
    public String name() {
        return JOB_NAME;
    }

    /** 17:15 Swiss time: the ECB publishes the day's reference rates around 16:00 CET. */
    @Override
    @Scheduled(cron = "0 15 17 * * *", zone = "Europe/Zurich")
    public SyncRun run() {
        return syncRunner.run(JOB_NAME, this::sync);
    }

    private int sync() {
        List<CurrencyCode> currencies = heldCurrencies.findForeign();
        if (currencies.isEmpty()) {
            log.info("No foreign-currency instruments — nothing to convert");
            return 0;
        }
        if (providers.isEmpty()) {
            log.warn("FX sync ran with no providers enabled");
            return 0;
        }
        LocalDate today = LocalDate.now(SwissTime.ZONE);
        log.info("Syncing fx rates for {} currencies from {} providers", currencies.size(), providers.size());

        int stored = 0;
        for (FxRateProvider provider : providers) {
            for (CurrencyCode currency : currencies) {
                stored += syncLatest(provider, currency, today);
                stored += backfillIfThin(provider, currency, today);
            }
        }
        return stored;
    }

    private int syncLatest(FxRateProvider provider, CurrencyCode currency, LocalDate today) {
        SourceResult<FxRate> result = provider.rate(currency, today);
        switch (result) {
            case SourceResult.Found<FxRate>(var rate) -> {
                writer.store(rate);
                return 1;
            }
            case SourceResult.NotFound<FxRate> ignored ->
                    log.debug("{} has no {} rate for {}", provider.name(), currency, today);
            case SourceResult.Unavailable<FxRate>(var reason) ->
                    log.warn("{} unavailable for {}: {}", provider.name(), currency, reason);
        }
        return 0;
    }

    /**
     * Fills a currency's history the first time it is seen. Only Frankfurter answers a range, so
     * this is a no-op for BAZG (its {@code rates} is empty) — the official rate is a today-only
     * figure with no historical series to reconstruct.
     */
    private int backfillIfThin(FxRateProvider provider, CurrencyCode currency, LocalDate today) {
        if (repository.countByCurrencyAndRateType(currency.value(), provider.type()) >= BACKFILL_THRESHOLD) {
            return 0;
        }
        int stored = 0;
        for (int year = today.getYear() - BACKFILL_YEARS; year <= today.getYear(); year++) {
            LocalDate from = LocalDate.of(year, 1, 1);
            LocalDate to = year == today.getYear() ? today : LocalDate.of(year, 12, 31);
            for (FxRate rate : provider.rates(currency, from, to)) {
                writer.store(rate);
                stored++;
            }
        }
        if (stored > 0) {
            log.info("Backfilled {} {} rates from {}", stored, currency, provider.name());
        }
        return stored;
    }
}
