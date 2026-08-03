package ch.finyo.fx;

import ch.finyo.common.SwissTime;
import ch.finyo.common.money.CurrencyCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Runs the FX sync outside its nightly schedule, when waiting for 17:15 would leave a hole.
 *
 * Two such holes exist. A dev laptop that is asleep at 17:15 never runs the cron, so foreign
 * positions lose their CHF value until someone remembers the admin trigger — the startup catch-up
 * closes that one. And a freshly created position in a never-held currency has no rate at all
 * until the next night — {@link #ensureRates} closes that one.
 *
 * Everything here is asynchronous and failure-swallowing: a slow or dead Frankfurter must delay
 * neither application startup nor position creation. The {@link FxRateSyncJob} is injected as an
 * {@link ObjectProvider} because it only exists when {@code finyo.sync.enabled} is true — when
 * sync is off, this service is inert without needing its own condition, and the port stays
 * injectable for the investment module.
 *
 * <p><b>Known gap, accepted:</b> a catch-up that overlaps a running nightly or startup sync loses
 * the {@link ch.finyo.sync.SyncRunner} lock and is recorded as SKIPPED — and, being
 * fire-and-forget, is not retried. The overlapping run does not know the brand-new currency
 * either, so its rate arrives one nightly run later. Rare enough (a position created during the
 * seconds a sync is in flight) that a retry queue would be more machinery than the hole is worth.
 */
@Slf4j
@Component
public class FxRateCatchUpService implements FxRateCatchUp {

    /**
     * A MID rate this many days old still counts as fresh. Rule: the ECB publishes no rates on
     * weekends and holidays, so on an Easter Monday the newest honest rate is three days old —
     * that is a gap, not staleness. Anything older means the nightly job was actually missed
     * and is worth catching up immediately instead of waiting for the next 17:15.
     */
    static final int FRESHNESS_TOLERANCE_DAYS = 3;

    private final FxRateRepository repository;
    private final HeldCurrenciesQuery heldCurrencies;
    private final ObjectProvider<FxRateSyncJob> syncJob;
    private final Executor executor;

    @Autowired
    public FxRateCatchUpService(FxRateRepository repository,
                                HeldCurrenciesQuery heldCurrencies,
                                ObjectProvider<FxRateSyncJob> syncJob) {
        // One virtual thread per catch-up: these are rare, network-bound, and short-lived —
        // exactly what virtual threads are for, and no pool to size or shut down.
        this(repository, heldCurrencies, syncJob, Thread::startVirtualThread);
    }

    FxRateCatchUpService(FxRateRepository repository,
                         HeldCurrenciesQuery heldCurrencies,
                         ObjectProvider<FxRateSyncJob> syncJob,
                         Executor executor) {
        this.repository = repository;
        this.heldCurrencies = heldCurrencies;
        this.syncJob = syncJob;
        this.executor = executor;
    }

    /**
     * After startup — never during it: {@code ApplicationReadyEvent} fires once the application
     * is serving, so a hanging provider cannot stall the boot, and the async hop below keeps it
     * off the startup thread entirely.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        runAsync("startup fx catch-up", this::catchUpIfStale);
    }

    @Override
    public void ensureRates(Collection<CurrencyCode> currencies) {
        List<CurrencyCode> candidates = currencies.stream()
                .filter(Objects::nonNull)
                .filter(currency -> !currency.isChf())
                .distinct()
                .toList();
        if (candidates.isEmpty()) {
            return;
        }
        // One task for the whole batch, checked sequentially: N tasks would race each other for
        // the sync lock, and the first currency's three-year backfill would make every other one
        // a SKIPPED run that nobody retries.
        runAsync("fx catch-up for " + candidates, () -> syncUnknown(candidates));
    }

    void catchUpIfStale() {
        FxRateSyncJob job = syncJob.getIfAvailable();
        if (job == null) {
            return; // finyo.sync.enabled=false — nothing may reach the network
        }
        LocalDate today = LocalDate.now(SwissTime.ZONE);
        List<CurrencyCode> stale = heldCurrencies.findForeign().stream()
                .filter(currency -> !hasFreshMidRate(currency, today))
                .toList();
        if (stale.isEmpty()) {
            log.debug("All held foreign currencies have a fresh MID rate — no catch-up needed");
            return;
        }
        log.info("MID rates missing or stale for {} — running fx sync now", stale);
        job.run();
    }

    void syncUnknown(List<CurrencyCode> candidates) {
        FxRateSyncJob job = syncJob.getIfAvailable();
        if (job == null) {
            return;
        }
        // "No rate at all", not the freshness rule: a merely stale currency is the startup /
        // nightly job's business, but a currency without a single MID rate cannot be valued
        // even approximately — that is the case worth an immediate fetch.
        List<CurrencyCode> missing = candidates.stream()
                .filter(currency -> repository.countByCurrencyAndRateType(currency.value(), FxRateType.MID) == 0)
                .toList();
        if (missing.isEmpty()) {
            return;
        }
        log.info("No MID rate stored for new {} — running fx sync now", missing);
        job.run(missing);
    }

    private boolean hasFreshMidRate(CurrencyCode currency, LocalDate today) {
        return repository
                .findTopByCurrencyAndRateTypeAndRateDateLessThanEqualOrderByRateDateDesc(
                        currency.value(), FxRateType.MID, today)
                .filter(rate -> !rate.getRateDate().isBefore(today.minusDays(FRESHNESS_TOLERANCE_DAYS)))
                .isPresent();
    }

    private void runAsync(String what, Runnable work) {
        executor.execute(() -> {
            try {
                work.run();
            } catch (RuntimeException e) {
                // Deliberately swallowed: a catch-up is opportunistic. The nightly job and the
                // admin trigger both still exist, and SyncRunner records any run that got far
                // enough to matter — this catch only covers what happens before one starts.
                log.warn("{} failed: {}", what, e.getMessage(), e);
            }
        });
    }
}
