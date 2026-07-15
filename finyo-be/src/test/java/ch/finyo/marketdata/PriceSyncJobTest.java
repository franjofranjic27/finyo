package ch.finyo.marketdata;

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
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

/**
 * Unit tests for PriceSyncJob.
 *
 * The job is deliberately narrow: it prices the securities users actually hold, not a
 * universe. That matters because SIX FQS cannot batch — a multi-ISIN {@code where} clause
 * silently returns only the first match (probed against the live endpoint) — so every
 * security costs one request to a source finyo is merely tolerated on.
 */
@DisplayName("PriceSyncJob")
@ExtendWith(MockitoExtension.class)
class PriceSyncJobTest {

    @Mock
    private MarketDataService marketData;

    @Mock
    private HeldIsinsQuery heldIsins;

    @Mock
    private SyncRunner syncRunner;

    @InjectMocks
    private PriceSyncJob job;

    private SyncRun recordedRun;

    @Test
    void is_registered_under_a_stable_name_so_an_admin_can_trigger_it() {
        assertThat(job.name()).isEqualTo("prices");
    }

    @Test
    void refreshes_exactly_the_securities_somebody_holds() {
        given(heldIsins.findAll()).willReturn(List.of("CH0038863350", "IE00B4L5Y983"));
        given(marketData.refresh(List.of("CH0038863350", "IE00B4L5Y983"))).willReturn(2);
        givenTheRunnerExecutesTheWork();

        job.run();

        then(marketData).should().refresh(List.of("CH0038863350", "IE00B4L5Y983"));
        assertThat(recordedRun.getItemsProcessed()).isEqualTo(2);
    }

    @Test
    void reports_the_number_actually_priced_rather_than_the_number_attempted() {
        // Most of a Swiss 3a portfolio is unlisted, so "asked about ten, priced four" is the
        // normal night. Reporting ten would make sync_run a comfort blanket rather than a
        // signal — the whole point of the row is that it can say something went wrong.
        given(heldIsins.findAll()).willReturn(List.of("CH0038863350", "CH0214967314"));
        given(marketData.refresh(any())).willReturn(1);
        givenTheRunnerExecutesTheWork();

        job.run();

        assertThat(recordedRun.getItemsProcessed()).isEqualTo(1);
    }

    @Test
    void asks_no_provider_anything_when_nobody_holds_a_security_with_an_isin() {
        // A fresh installation, or a portfolio of nothing but name-only holdings. Calling a
        // rate-limited vendor with an empty list would be pure waste.
        given(heldIsins.findAll()).willReturn(List.of());
        givenTheRunnerExecutesTheWork();

        job.run();

        then(marketData).should(never()).refresh(any());
        assertThat(recordedRun.getItemsProcessed()).isZero();
    }

    @Test
    void hands_its_work_to_the_runner_rather_than_running_unguarded() {
        given(heldIsins.findAll()).willReturn(List.of());
        givenTheRunnerExecutesTheWork();

        job.run();

        then(syncRunner).should().run(eq("prices"), any(IntSupplier.class));
    }

    /** SyncRunner stands in as a pass-through that executes the work and records the result. */
    private void givenTheRunnerExecutesTheWork() {
        given(syncRunner.run(eq("prices"), any(IntSupplier.class)))
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
