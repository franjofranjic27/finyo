package ch.finyo.sync;

/**
 * A background sync that can also be triggered by hand.
 *
 * The interface exists so the admin endpoint can offer every job without knowing any of them —
 * adding a job (FX rates, tax multipliers) makes it triggerable with no change here.
 */
public interface SyncJob {

    /** Stable name, used in the URL and in {@code sync_run.job_name}. */
    String name();

    /**
     * @return the recorded run. Returned rather than looked up afterwards on purpose: an admin
     *         trigger that then searched for "the newest run of this job" could pick up a
     *         concurrent cron attempt's SKIPPED row instead of its own result.
     */
    SyncRun run();
}
