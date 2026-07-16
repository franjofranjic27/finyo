package ch.finyo.sync;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lets an admin see what the background jobs did, and run one by hand.
 *
 * The manual trigger is what makes a failed nightly run recoverable without a redeploy — the
 * difference between "the prices are stale until tomorrow night" and "fixed in ten seconds".
 * {@link SyncRunner} makes sure it cannot collide with the scheduled run.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/sync")
@Tag(name = "Admin", description = "Background sync status and manual triggers")
@PreAuthorize("hasRole('admin')")
public class SyncAdminController {

    private final SyncRunRepository repository;
    private final Map<String, SyncJob> jobs;

    public SyncAdminController(SyncRunRepository repository, List<SyncJob> jobs) {
        this.repository = repository;
        this.jobs = jobs.stream().collect(Collectors.toMap(SyncJob::name, Function.identity()));
    }

    @GetMapping("/runs")
    @Operation(summary = "The last 20 sync runs, newest first")
    public List<SyncRunResponse> recentRuns() {
        return repository.findTop20ByOrderByStartedAtDesc().stream()
                .map(SyncRunResponse::from)
                .toList();
    }

    @PostMapping("/{jobName}")
    @Operation(summary = "Run a sync job now")
    public SyncRunResponse trigger(@PathVariable String jobName) {
        SyncJob job = jobs.get(jobName);
        if (job == null) {
            throw new IllegalArgumentException("Unknown sync job '" + jobName + "'. Known: " + jobs.keySet());
        }
        // Runs synchronously in the request thread. For the price sync that is one SIX call per
        // held ISIN with no batching, so this can take tens of seconds and may hit a proxy
        // timeout — the run still completes in the background, and its result is on /runs. The
        // job returns its own record, so a concurrent cron attempt's SKIPPED row cannot be
        // mistaken for this trigger's outcome.
        log.info("Admin triggered sync '{}'", jobName);
        return SyncRunResponse.from(job.run());
    }
}
