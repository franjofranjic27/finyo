package ch.finyo.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntSupplier;

/**
 * Runs a background job under a lock and writes down what happened.
 *
 * Two guarantees, both there because their absence is silent:
 *
 * <ul>
 *   <li><b>A job never runs twice at once.</b> The manual admin trigger exists precisely so a
 *       failed nightly run can be repeated — which means somebody will eventually press it
 *       while the nightly run is going. The second one stands down instead of fighting the
 *       first over the same rows.</li>
 *   <li><b>It leaves a trace.</b> A job that has quietly failed every night for a month looks
 *       exactly like a job with nothing to do. {@code sync_run} is the difference between
 *       "the prices are old and nobody knows why" and an answer.</li>
 * </ul>
 *
 * <p><b>An in-process lock, not a Postgres advisory lock</b> — and that is a considered
 * choice, not a shortcut. An advisory lock lives for the length of its transaction, so holding
 * one across a sync would mean holding a database connection open across dozens of HTTP calls:
 * exactly the pattern this codebase just spent a PR removing from the read path. finyo runs as
 * a single instance, so a JVM lock covers both races that actually exist (the admin trigger
 * crossing the cron run, and a re-run after a failure). The audit rows are written in their own
 * short transactions, and no connection is held while the network is being waited on.
 *
 * <p>The day finyo runs as more than one instance, this is no longer enough. That is worth
 * knowing before it happens rather than after.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncRunner {

    private final SyncRunRepository repository;
    private final Map<String, Lock> locks = new ConcurrentHashMap<>();

    /**
     * @param work returns the number of items processed
     * @return the recorded run
     */
    public SyncRun run(String jobName, IntSupplier work) {
        Lock lock = locks.computeIfAbsent(jobName, _ -> new ReentrantLock());
        if (!lock.tryLock()) {
            log.info("Sync '{}' is already running — standing down", jobName);
            return record(jobName, SyncStatus.SKIPPED, now(), null, "another run is in progress");
        }

        OffsetDateTime startedAt = now();
        try {
            log.info("Sync '{}' started", jobName);
            int processed = work.getAsInt();
            log.info("Sync '{}' finished: {} items", jobName, processed);
            return record(jobName, SyncStatus.SUCCESS, startedAt, processed, null);
        } catch (RuntimeException e) {
            // Recorded rather than rethrown: a scheduled job that throws vanishes into the
            // scheduler's own log and nobody ever looks there. The row is the alarm.
            log.error("Sync '{}' failed", jobName, e);
            return record(jobName, SyncStatus.FAILED, startedAt, null,
                    truncate(e.getClass().getSimpleName() + ": " + e.getMessage()));
        } finally {
            lock.unlock();
        }
    }

    /**
     * No {@code @Transactional} here, and that is on purpose rather than an oversight: this is
     * called from {@link #run} on the same bean, so Spring's proxy would be bypassed entirely
     * and the annotation would promise a boundary it does not create. {@code save()} carries
     * its own transaction (SimpleJpaRepository is annotated), which is all the audit row needs.
     */
    private SyncRun record(String jobName, SyncStatus status, OffsetDateTime startedAt,
                           Integer itemsProcessed, String message) {
        return repository.save(SyncRun.builder()
                .jobName(jobName)
                .status(status)
                .startedAt(startedAt)
                .finishedAt(now())
                .itemsProcessed(itemsProcessed)
                .message(message)
                .build());
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private static String truncate(String message) {
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
