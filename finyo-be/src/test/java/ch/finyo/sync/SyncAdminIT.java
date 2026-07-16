package ch.finyo.sync;

import ch.finyo.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for /api/v1/admin/sync.
 *
 * The endpoint is what makes a failed nightly run recoverable without a redeploy — the
 * difference between "the prices are stale until tomorrow night" and "fixed in ten seconds".
 * So it has to actually run the job, and the run has to leave a trace.
 *
 * <p><b>Sync is switched on for this context only.</b> The test profile disables it, because a
 * scheduled job firing mid-test would reach for the network. Enabling it here is what brings
 * the {@code SyncJob} beans into existence at all — without them the admin endpoint would have
 * an empty job map and every trigger would be a 400, which would pass for the wrong reason.
 *
 * No network traffic results: the quote provider chain is empty in the test profile, so the
 * price job iterates the (empty) list of held ISINs and asks nobody anything. The cron
 * expressions fire at 22:30 and 23:00 Europe/Zurich and are irrelevant here — nothing is
 * awaited, and the tests trigger the jobs by hand.
 */
@DisplayName("Sync admin API (Postgres)")
@TestPropertySource(properties = "finyo.sync.enabled=true")
class SyncAdminIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SyncRunRepository syncRunRepository;

    @Autowired
    private List<SyncJob> jobs;

    @BeforeEach
    void cleanTable() {
        syncRunRepository.deleteAll();
    }

    // =========================================================================
    // Wiring
    // =========================================================================

    @Test
    void every_background_job_is_wired_and_therefore_triggerable() {
        // The admin endpoint offers whatever implements SyncJob, so a job that is not a bean
        // is a job nobody can rescue when it fails.
        assertThat(jobs)
                .extracting(SyncJob::name)
                .containsExactlyInAnyOrder("prices", "portfolio-snapshots", "fx-rates");
    }

    // =========================================================================
    // Authorisation
    // =========================================================================

    @Test
    void an_ordinary_user_may_neither_read_the_runs_nor_trigger_a_job() throws Exception {
        // Triggering a sync hits external vendors and rewrites market data for everybody.
        // It is not a user-level operation.
        mockMvc.perform(get("/api/v1/admin/sync/runs").with(asUser()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/sync/{job}", "prices").with(asUser()))
                .andExpect(status().isForbidden());

        assertThat(syncRunRepository.count()).isZero();
    }

    @Test
    void an_anonymous_caller_is_rejected_outright() throws Exception {
        mockMvc.perform(get("/api/v1/admin/sync/runs"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Triggering a job
    // =========================================================================

    @Test
    void an_admin_can_run_a_job_by_hand_and_it_leaves_a_record() throws Exception {
        mockMvc.perform(post("/api/v1/admin/sync/{job}", "prices").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobName", is("prices")))
                .andExpect(jsonPath("$.status", is("SUCCESS")));

        // The row is the whole point: a job that has been failing quietly every night looks
        // exactly like a job with nothing to do until somebody can read this.
        SyncRun recorded = syncRunRepository.findTop20ByOrderByStartedAtDesc().getFirst();
        assertThat(recorded.getJobName()).isEqualTo("prices");
        assertThat(recorded.getStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(recorded.getStartedAt()).isNotNull();
        assertThat(recorded.getFinishedAt()).isNotNull();
        // Nobody holds a security in this context, and no quote provider is configured — so
        // zero priced is the correct answer, and a success rather than a failure.
        assertThat(recorded.getItemsProcessed()).isZero();
    }

    @Test
    void an_unknown_job_is_a_bad_request_rather_than_a_silent_no_op() throws Exception {
        // A typo in the job name must not look like a successful trigger, or an operator will
        // sit waiting for prices that were never fetched.
        mockMvc.perform(post("/api/v1/admin/sync/{job}", "there-is-no-such-job").with(asAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Bad Request")));

        assertThat(syncRunRepository.count()).isZero();
    }

    // =========================================================================
    // Reading the history
    // =========================================================================

    @Test
    void the_runs_endpoint_reports_what_the_jobs_did_newest_first() throws Exception {
        mockMvc.perform(post("/api/v1/admin/sync/{job}", "prices").with(asAdmin()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/sync/{job}", "portfolio-snapshots").with(asAdmin()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/sync/runs").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)))
                .andExpect(jsonPath("$[0].jobName", is("portfolio-snapshots")))
                .andExpect(jsonPath("$[1].jobName", is("prices")));
    }

    @Test
    void the_runs_endpoint_is_empty_rather_than_absent_before_anything_has_run() throws Exception {
        mockMvc.perform(get("/api/v1/admin/sync/runs").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
    }
}
