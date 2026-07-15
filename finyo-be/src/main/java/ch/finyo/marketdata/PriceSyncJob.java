package ch.finyo.marketdata;

import ch.finyo.sync.SyncJob;
import ch.finyo.sync.SyncRun;
import ch.finyo.sync.SyncRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fetches closing prices for the securities users actually hold, once a night.
 *
 * The scope is deliberately small: the distinct ISINs that appear in {@code instrument}, not a
 * universe. For a personal finance tool that is a few dozen securities, so a few dozen requests
 * — which matters, because SIX FQS cannot batch (probed: a multi-ISIN {@code where} clause
 * silently returns only the first match) and finyo is merely tolerated on that endpoint.
 *
 * Running it at night rather than on demand is the whole point of the PR: the read path is now
 * pure database, so a slow or dead vendor can no longer make the portfolio page slow or dead.
 * The cost is that prices are up to a day old — stated openly in the UI rather than hidden.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "finyo.sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PriceSyncJob implements SyncJob {

    public static final String JOB_NAME = "prices";

    private final MarketDataService marketData;
    private final HeldIsinsQuery heldIsins;
    private final SyncRunner syncRunner;

    @Override
    public String name() {
        return JOB_NAME;
    }

    /** 22:30 Swiss time: after the SIX close, before the snapshot job at 23:00. */
    @Override
    @Scheduled(cron = "0 30 22 * * *", zone = "Europe/Zurich")
    public SyncRun run() {
        return syncRunner.run(JOB_NAME, this::refreshHeldSecurities);
    }

    private int refreshHeldSecurities() {
        List<String> isins = heldIsins.findAll();
        if (isins.isEmpty()) {
            log.info("No instruments with an ISIN — nothing to price");
            return 0;
        }
        log.info("Refreshing prices for {} securities", isins.size());
        return marketData.refresh(isins);
    }
}
