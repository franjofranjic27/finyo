package ch.finyo.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;

/**
 * Unit tests for SyncRunner.
 *
 * Everything this class does exists because its absence would be <em>silent</em>, and the
 * tests are written to that:
 *
 * <ul>
 *   <li><b>A failure is recorded, not thrown.</b> A scheduled job that throws disappears into
 *       the scheduler's own log, which nobody reads. A job that has been failing every night
 *       for three months then looks exactly like a job with nothing to do. The row is the
 *       alarm — so the exception must be caught, and the row must carry the reason.</li>
 *   <li><b>A job never runs twice at once.</b> The manual admin trigger exists precisely so a
 *       failed nightly run can be repeated, which means somebody will eventually press it
 *       while the nightly run is going. The second one must stand down — and SKIPPED is not a
 *       failure, because nothing went wrong.</li>
 * </ul>
 */
@DisplayName("SyncRunner")
@ExtendWith(MockitoExtension.class)
class SyncRunnerTest {

    private static final String JOB = "prices";

    @Mock
    private SyncRunRepository repository;

    @InjectMocks
    private SyncRunner syncRunner;

    @BeforeEach
    void repositoryEchoesWhatItIsGiven() {
        given(repository.save(any(SyncRun.class))).willAnswer(invocation -> invocation.getArgument(0));
    }

    // =========================================================================
    // The audit row
    // =========================================================================

    @Nested
    @DisplayName("writes down what happened")
    class TheAuditRow {

        @Test
        void records_a_successful_run_with_the_number_of_items_it_got_done() {
            SyncRun run = syncRunner.run(JOB, () -> 7);

            assertThat(run.getJobName()).isEqualTo(JOB);
            assertThat(run.getStatus()).isEqualTo(SyncStatus.SUCCESS);
            assertThat(run.getItemsProcessed()).isEqualTo(7);
            assertThat(run.getMessage()).isNull();
            assertThat(run.getStartedAt()).isNotNull();
            assertThat(run.getFinishedAt()).isNotNull();
            assertThat(run.getFinishedAt()).isAfterOrEqualTo(run.getStartedAt());
        }

        @Test
        void records_a_successful_run_that_had_nothing_to_do() {
            // Zero items is a success, not a failure — nobody holds any securities yet. If it
            // were recorded as FAILED, the one signal that matters would be worthless.
            SyncRun run = syncRunner.run(JOB, () -> 0);

            assertThat(run.getStatus()).isEqualTo(SyncStatus.SUCCESS);
            assertThat(run.getItemsProcessed()).isZero();
        }

        @Test
        void records_a_failed_run_and_does_not_rethrow() {
            // The exception must die here. Rethrown, it goes to the scheduler's log and the
            // job is simply never heard from again.
            AtomicReference<SyncRun> recorded = new AtomicReference<>();

            assertThatCode(() -> recorded.set(syncRunner.run(JOB, () -> {
                throw new IllegalStateException("SIX changed its payload");
            }))).doesNotThrowAnyException();

            SyncRun run = recorded.get();
            assertThat(run.getStatus()).isEqualTo(SyncStatus.FAILED);
            assertThat(run.getMessage())
                    .contains("IllegalStateException")
                    .contains("SIX changed its payload");
            assertThat(run.getItemsProcessed()).isNull();
            assertThat(run.getFinishedAt()).isNotNull();
        }

        @Test
        void truncates_a_failure_message_that_is_longer_than_the_column() {
            // message is VARCHAR(500). A vendor stack trace in the exception message must not
            // make the audit row itself fail to insert — which would lose the very record that
            // explains the failure.
            String verbose = "X".repeat(1_000);

            SyncRun run = syncRunner.run(JOB, () -> {
                throw new IllegalStateException(verbose);
            });

            assertThat(run.getMessage()).hasSize(500);
        }
    }

    // =========================================================================
    // The lock
    // =========================================================================

    @Nested
    @DisplayName("never lets a job run twice at once")
    class TheLock {

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void stands_the_second_run_down_while_the_first_is_still_going() throws Exception {
            // The collision that will actually happen: an admin triggers the price sync by
            // hand while the nightly one is still working through the ISINs. Both writing the
            // same rows would be a race for no benefit, so the second one stands down.
            CountDownLatch firstRunIsInside = new CountDownLatch(1);
            CountDownLatch letTheFirstRunFinish = new CountDownLatch(1);
            AtomicReference<SyncRun> firstRun = new AtomicReference<>();

            Thread nightly = new Thread(() -> firstRun.set(syncRunner.run(JOB, () -> {
                firstRunIsInside.countDown();
                await(letTheFirstRunFinish);
                return 5;
            })));
            nightly.start();
            assertThat(firstRunIsInside.await(5, TimeUnit.SECONDS)).isTrue();

            SyncRun manualTrigger = syncRunner.run(JOB, () -> 99);

            letTheFirstRunFinish.countDown();
            nightly.join();

            assertThat(manualTrigger.getStatus()).isEqualTo(SyncStatus.SKIPPED);
            assertThat(manualTrigger.getMessage()).contains("in progress");
            // SKIPPED means "did not run", so there is no item count to report — reporting 0
            // would be indistinguishable from a run that found nothing to do.
            assertThat(manualTrigger.getItemsProcessed()).isNull();
            // And the first run is untouched by the collision: it finishes and reports its work.
            assertThat(firstRun.get().getStatus()).isEqualTo(SyncStatus.SUCCESS);
            assertThat(firstRun.get().getItemsProcessed()).isEqualTo(5);
        }

        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void lets_a_different_job_run_while_one_is_busy() throws Exception {
            // The lock is per job, not global. A price sync that is slow because SIX is slow
            // must not also stop the snapshot job from ever running.
            CountDownLatch priceSyncIsInside = new CountDownLatch(1);
            CountDownLatch letThePriceSyncFinish = new CountDownLatch(1);

            Thread prices = new Thread(() -> syncRunner.run(JOB, () -> {
                priceSyncIsInside.countDown();
                await(letThePriceSyncFinish);
                return 1;
            }));
            prices.start();
            assertThat(priceSyncIsInside.await(5, TimeUnit.SECONDS)).isTrue();

            SyncRun snapshots = syncRunner.run("portfolio-snapshots", () -> 3);

            letThePriceSyncFinish.countDown();
            prices.join();

            assertThat(snapshots.getStatus()).isEqualTo(SyncStatus.SUCCESS);
            assertThat(snapshots.getItemsProcessed()).isEqualTo(3);
        }

        @Test
        void releases_the_lock_after_a_failure_so_the_job_can_be_retried() {
            // Without this, one crash would wedge the job until the next redeploy — and the
            // manual trigger, whose entire purpose is recovering from that crash, would answer
            // SKIPPED forever.
            syncRunner.run(JOB, () -> {
                throw new IllegalStateException("boom");
            });

            SyncRun retry = syncRunner.run(JOB, () -> 2);

            assertThat(retry.getStatus()).isEqualTo(SyncStatus.SUCCESS);
            assertThat(retry.getItemsProcessed()).isEqualTo(2);
        }
    }

    /** The job body runs on another thread; an interrupt there must not be swallowed silently. */
    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the test never released the job");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
