package ch.finyo.taxdocument;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the configured cloud folders on a schedule.
 *
 * <p>Polling rather than Graph webhooks, deliberately: a change notification for a
 * file carries no detail, so a delta query has to follow anyway; subscriptions
 * expire after about 30 days and need renewing; and a missed notification means a
 * document silently never arrives. A poll is self-healing — whatever a failed run
 * missed, the next one picks up.
 *
 * <p>Only exists when finyo.graph.enabled=true. Without that guard the job would
 * start in every @SpringBootTest context and fire at a Graph endpoint that isn't
 * there.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "finyo.graph.enabled", havingValue = "true")
public class DocumentSyncJob {

    private final DocumentSyncService documentSyncService;

    @Scheduled(fixedDelayString = "${finyo.graph.sync-interval:PT15M}", initialDelayString = "PT1M")
    public void sync() {
        try {
            DocumentSyncService.SyncResult result = documentSyncService.syncAllEnabled();
            log.info("Scheduled document sync finished: {}", result);
        } catch (RuntimeException e) {
            // A scheduled task that throws is silently dropped from the schedule in
            // some setups — swallow, log, and let the next run retry.
            log.error("Scheduled document sync failed: {}", e.getMessage(), e);
        }
    }
}
