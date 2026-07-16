package ch.finyo.investment;

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
 * Writes each user's daily portfolio snapshot.
 *
 * This used to happen inside {@code getPortfolio()} — a GET that mutated state. Two things were
 * wrong with that, beyond the obvious. The history had a hole on every day the user did not log
 * in, so the performance chart was really a chart of when they visited. And the snapshot was
 * taken at whatever moment they happened to open the page, using whatever price was current
 * then, which makes two users' charts incomparable and a single user's chart jumpy for reasons
 * that have nothing to do with the market.
 *
 * A nightly job at a fixed time, after the prices are in, fixes both. The read becomes a read.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "finyo.sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PortfolioSnapshotJob implements SyncJob {

    public static final String JOB_NAME = "portfolio-snapshots";

    private final PositionRepository positionRepository;
    private final PortfolioService portfolioService;
    private final SyncRunner syncRunner;

    @Override
    public String name() {
        return JOB_NAME;
    }

    /** 23:00 Swiss time — after PriceSyncJob at 22:30, so the snapshot uses the day's closes. */
    @Override
    @Scheduled(cron = "0 0 23 * * *", zone = "Europe/Zurich")
    public SyncRun run() {
        return syncRunner.run(JOB_NAME, this::snapshotEveryUser);
    }

    private int snapshotEveryUser() {
        List<String> userIds = positionRepository.findDistinctUserIds();
        int written = 0;
        for (String userId : userIds) {
            try {
                portfolioService.writeSnapshot(userId);
                written++;
            } catch (RuntimeException e) {
                // One user's broken data must not cost every other user their history.
                log.warn("Could not snapshot portfolio for user={}: {}", userId, e.getMessage());
            }
        }
        return written;
    }
}
