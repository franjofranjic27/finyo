package ch.finyo.fx;

import ch.finyo.common.SourceResult;
import ch.finyo.common.money.CurrencyCode;
import ch.finyo.fx.spi.FxRateProvider;
import ch.finyo.sync.SyncRun;
import ch.finyo.sync.SyncRunner;
import ch.finyo.sync.SyncStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.IntSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.times;

/**
 * Unit tests for FxRateSyncJob.
 *
 * The job is deliberately narrow, like the price sync: it fetches rates only for the currencies
 * users actually hold. The two behaviours worth pinning are that it stores today's rate for each,
 * and that it backfills a currency's history exactly once — when it is thin — rather than every
 * night.
 */
@DisplayName("FxRateSyncJob")
@ExtendWith(MockitoExtension.class)
class FxRateSyncJobTest {

    private static final CurrencyCode EUR = new CurrencyCode("EUR");

    @Mock
    private HeldCurrenciesQuery heldCurrencies;

    @Mock
    private FxRateRepository repository;

    @Mock
    private FxRateWriter writer;

    @Mock
    private SyncRunner syncRunner;

    private SyncRun recordedRun;

    private FxRateSyncJob job(FxRateProvider... providers) {
        return new FxRateSyncJob(List.of(providers), heldCurrencies, repository, writer, syncRunner);
    }

    private static FxRate rate(LocalDate date) {
        return FxRate.builder()
                .currency("EUR").rateDate(date).chfPerUnit(new BigDecimal("0.9250"))
                .rateType(FxRateType.MID).source("frankfurter")
                .retrievedAt(OffsetDateTime.now(ZoneOffset.UTC)).build();
    }

    @Test
    void is_registered_under_a_stable_name_so_an_admin_can_trigger_it() {
        assertThat(job(new StubProvider("frankfurter", FxRateType.MID)).name()).isEqualTo("fx-rates");
    }

    @Test
    void stores_todays_rate_for_each_held_currency_without_backfilling_when_history_is_present() {
        given(heldCurrencies.findForeign()).willReturn(List.of(EUR));
        given(repository.countByCurrencyAndRateType("EUR", FxRateType.MID)).willReturn(500L);
        StubProvider frankfurter = new StubProvider("frankfurter", FxRateType.MID);
        frankfurter.latest = SourceResult.found(rate(LocalDate.of(2026, 7, 14)));
        frankfurter.range = List.of(rate(LocalDate.of(2025, 1, 2))); // must NOT be used
        givenTheRunnerExecutesTheWork();

        job(frankfurter).run();

        then(writer).should(times(1)).store(any());
        assertThat(frankfurter.rangeCalls).isZero();
        assertThat(recordedRun.getItemsProcessed()).isEqualTo(1);
    }

    @Test
    void backfills_a_currency_whose_history_is_thin() {
        given(heldCurrencies.findForeign()).willReturn(List.of(EUR));
        given(repository.countByCurrencyAndRateType("EUR", FxRateType.MID)).willReturn(0L);
        StubProvider frankfurter = new StubProvider("frankfurter", FxRateType.MID);
        frankfurter.latest = SourceResult.found(rate(LocalDate.of(2026, 7, 14)));
        frankfurter.range = List.of(rate(LocalDate.of(2025, 1, 2)), rate(LocalDate.of(2025, 1, 3)));
        givenTheRunnerExecutesTheWork();

        job(frankfurter).run();

        // Today's rate plus the backfilled range, once per year across the window.
        then(writer).should(times(1 + frankfurter.range.size() * frankfurter.rangeCalls)).store(any());
        assertThat(frankfurter.rangeCalls).isPositive();
    }

    @Test
    void asks_no_provider_anything_when_nobody_holds_a_foreign_currency() {
        given(heldCurrencies.findForeign()).willReturn(List.of());
        givenTheRunnerExecutesTheWork();

        job(new StubProvider("frankfurter", FxRateType.MID)).run();

        then(writer).should(never()).store(any());
        assertThat(recordedRun.getItemsProcessed()).isZero();
    }

    @Test
    void hands_its_work_to_the_runner_rather_than_running_unguarded() {
        given(heldCurrencies.findForeign()).willReturn(List.of());
        givenTheRunnerExecutesTheWork();

        job(new StubProvider("frankfurter", FxRateType.MID)).run();

        then(syncRunner).should().run(eq("fx-rates"), any(IntSupplier.class));
    }

    private void givenTheRunnerExecutesTheWork() {
        given(syncRunner.run(eq("fx-rates"), any(IntSupplier.class)))
                .willAnswer(invocation -> {
                    recordedRun = SyncRun.builder()
                            .jobName(invocation.getArgument(0))
                            .status(SyncStatus.SUCCESS)
                            .itemsProcessed(invocation.getArgument(1, IntSupplier.class).getAsInt())
                            .build();
                    return recordedRun;
                });
    }

    /** A controllable provider — a mock would obscure the name/type contract the chain relies on. */
    private static final class StubProvider implements FxRateProvider {
        private final String name;
        private final FxRateType type;
        private SourceResult<FxRate> latest = SourceResult.notFound();
        private List<FxRate> range = List.of();
        private int rangeCalls;

        private StubProvider(String name, FxRateType type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public FxRateType type() {
            return type;
        }

        @Override
        public SourceResult<FxRate> rate(CurrencyCode currency, LocalDate on) {
            return latest;
        }

        @Override
        public List<FxRate> rates(CurrencyCode currency, LocalDate from, LocalDate to) {
            rangeCalls++;
            return range;
        }
    }
}
