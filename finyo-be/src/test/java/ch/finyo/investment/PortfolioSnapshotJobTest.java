package ch.finyo.investment;

import ch.finyo.sync.SyncRun;
import ch.finyo.sync.SyncRunner;
import ch.finyo.sync.SyncStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.IntSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

/**
 * Unit tests for PortfolioSnapshotJob.
 *
 * The job exists because {@code getPortfolio()} used to write the snapshot — a GET that
 * mutated state, and worse: the history had a hole on every day the user did not log in, so
 * the performance chart was really a chart of when they visited.
 *
 * What is tested here is mostly the failure behaviour, because that is where a nightly job
 * earns or loses its keep. <b>One user's broken data must not cost every other user their
 * history.</b> A single unhandled exception in the loop would end the run, and every user
 * after the broken one would silently lose that day — permanently, since a snapshot depends
 * on what they held then and cannot be reconstructed afterwards.
 *
 * SyncRunner is mocked and its work executed inline: whether the runner locks and records
 * correctly is SyncRunnerTest's subject, not this one's.
 */
@DisplayName("PortfolioSnapshotJob")
@ExtendWith(MockitoExtension.class)
class PortfolioSnapshotJobTest {

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private SyncRunner syncRunner;

    @InjectMocks
    private PortfolioSnapshotJob job;

    /** The run SyncRunner would have written to {@code sync_run}, captured by the stub below. */
    private SyncRun recordedRun;

    @Test
    void is_registered_under_a_stable_name_so_an_admin_can_trigger_it() {
        assertThat(job.name()).isEqualTo("portfolio-snapshots");
    }

    @Test
    void hands_its_work_to_the_runner_rather_than_running_unguarded() {
        // The lock and the audit row are the runner's job, and the job must go through it —
        // otherwise a manual trigger could collide with the nightly run, and a failure would
        // vanish into the scheduler's log.
        given(positionRepository.findDistinctUserIds()).willReturn(List.of());
        givenTheRunnerExecutesTheWork();

        job.run();

        then(syncRunner).should().run(eq("portfolio-snapshots"), any(IntSupplier.class));
    }

    @Test
    void snapshots_every_user_who_holds_something() {
        given(positionRepository.findDistinctUserIds()).willReturn(List.of("user-a", "user-b"));
        givenTheRunnerExecutesTheWork();

        job.run();

        then(portfolioService).should().writeSnapshot("user-a");
        then(portfolioService).should().writeSnapshot("user-b");
        assertThat(recordedRun.getItemsProcessed()).isEqualTo(2);
    }

    @Test
    void one_users_broken_data_does_not_cost_the_others_their_history() {
        // The load-bearing test. Without the per-user catch, the first user with, say, an
        // orphaned instrument ends the entire run — and everybody after them loses that day
        // for good, because a snapshot cannot be rebuilt after the fact.
        given(positionRepository.findDistinctUserIds()).willReturn(List.of("user-broken", "user-ok"));
        willThrow(new IllegalStateException("Instrument missing for position"))
                .given(portfolioService).writeSnapshot("user-broken");
        givenTheRunnerExecutesTheWork();

        job.run();

        then(portfolioService).should().writeSnapshot("user-ok");
        // The healthy user is snapshotted and counted; the broken one is not counted — which
        // is what makes the number in sync_run worth reading rather than reassuring.
        assertThat(recordedRun.getItemsProcessed()).isEqualTo(1);
    }

    @Test
    void reports_zero_rather_than_failing_when_nobody_holds_anything() {
        given(positionRepository.findDistinctUserIds()).willReturn(List.of());
        givenTheRunnerExecutesTheWork();

        job.run();

        assertThat(recordedRun.getItemsProcessed()).isZero();
        then(portfolioService).shouldHaveNoInteractions();
    }

    /** SyncRunner stands in as a pass-through that executes the work and records the result. */
    private void givenTheRunnerExecutesTheWork() {
        given(syncRunner.run(eq("portfolio-snapshots"), any(IntSupplier.class)))
                .willAnswer(invocation -> {
                    recordedRun = SyncRun.builder()
                            .jobName(invocation.getArgument(0))
                            .status(SyncStatus.SUCCESS)
                            .itemsProcessed(invocation.getArgument(1, IntSupplier.class).getAsInt())
                            .build();
                    return recordedRun;
                });
    }
}
