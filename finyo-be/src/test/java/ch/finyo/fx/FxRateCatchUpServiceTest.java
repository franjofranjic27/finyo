package ch.finyo.fx;

import ch.finyo.common.SwissTime;
import ch.finyo.common.money.CurrencyCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.anyList;
import static org.mockito.BDDMockito.anyString;
import static org.mockito.BDDMockito.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.times;

/**
 * Unit tests for FxRateCatchUpService.
 *
 * The two decisions worth pinning are the staleness rule at startup (a weekend-old rate is fresh,
 * an older one triggers the sync, a missing one too) and the new-currency trigger (only a currency
 * with no MID rate at all fetches immediately). The async hop is collapsed with a same-thread
 * executor so each test observes the decision, not the threading.
 */
@DisplayName("FxRateCatchUpService")
@ExtendWith(MockitoExtension.class)
class FxRateCatchUpServiceTest {

    private static final CurrencyCode USD = new CurrencyCode("USD");
    private static final CurrencyCode EUR = new CurrencyCode("EUR");
    private static final LocalDate TODAY = LocalDate.now(SwissTime.ZONE);

    @Mock
    private FxRateRepository repository;

    @Mock
    private HeldCurrenciesQuery heldCurrencies;

    @Mock
    private ObjectProvider<FxRateSyncJob> syncJobProvider;

    @Mock
    private FxRateSyncJob syncJob;

    private FxRateCatchUpService service;

    @BeforeEach
    void createServiceWithSameThreadExecutor() {
        service = new FxRateCatchUpService(repository, heldCurrencies, syncJobProvider, Runnable::run);
    }

    private void givenSyncIsEnabled() {
        given(syncJobProvider.getIfAvailable()).willReturn(syncJob);
    }

    private void givenLatestMidRateFor(CurrencyCode currency, LocalDate rateDate) {
        given(repository.findTopByCurrencyAndRateTypeAndRateDateLessThanEqualOrderByRateDateDesc(
                eq(currency.value()), eq(FxRateType.MID), any(LocalDate.class)))
                .willReturn(Optional.ofNullable(rateDate == null ? null : FxRate.builder()
                        .currency(currency.value()).rateDate(rateDate)
                        .chfPerUnit(new BigDecimal("0.8000")).rateType(FxRateType.MID)
                        .source("frankfurter").retrievedAt(OffsetDateTime.now(ZoneOffset.UTC))
                        .build()));
    }

    @Nested
    @DisplayName("startup catch-up")
    class StartupCatchUp {

        @Test
        void skips_the_sync_when_every_held_currency_has_a_rate_within_the_tolerance() {
            givenSyncIsEnabled();
            given(heldCurrencies.findForeign()).willReturn(List.of(USD));
            // Exactly at the tolerance boundary: a Friday rate seen on Easter Monday is fresh.
            givenLatestMidRateFor(USD, TODAY.minusDays(FxRateCatchUpService.FRESHNESS_TOLERANCE_DAYS));

            service.onApplicationReady();

            then(syncJob).should(never()).run();
        }

        @Test
        void runs_the_sync_when_a_currency_has_only_a_stale_rate() {
            givenSyncIsEnabled();
            given(heldCurrencies.findForeign()).willReturn(List.of(USD));
            givenLatestMidRateFor(USD, TODAY.minusDays(FxRateCatchUpService.FRESHNESS_TOLERANCE_DAYS + 1));

            service.onApplicationReady();

            then(syncJob).should().run();
        }

        @Test
        void runs_the_sync_when_a_currency_has_no_rate_at_all() {
            givenSyncIsEnabled();
            given(heldCurrencies.findForeign()).willReturn(List.of(USD));
            givenLatestMidRateFor(USD, null);

            service.onApplicationReady();

            then(syncJob).should().run();
        }

        @Test
        void does_nothing_when_nobody_holds_a_foreign_currency() {
            givenSyncIsEnabled();
            given(heldCurrencies.findForeign()).willReturn(List.of());

            service.onApplicationReady();

            then(syncJob).should(never()).run();
        }

        @Test
        void stays_inert_when_sync_is_disabled() {
            given(syncJobProvider.getIfAvailable()).willReturn(null);

            service.onApplicationReady();

            then(heldCurrencies).shouldHaveNoInteractions();
            then(repository).shouldHaveNoInteractions();
        }

        @Test
        void swallows_a_failure_instead_of_letting_it_reach_the_startup_listener() {
            givenSyncIsEnabled();
            given(heldCurrencies.findForeign()).willThrow(new IllegalStateException("db down"));

            assertThatNoException().isThrownBy(() -> service.onApplicationReady());
        }
    }

    @Nested
    @DisplayName("ensureRates")
    class EnsureRates {

        @Test
        void triggers_a_currency_scoped_sync_when_the_currency_has_no_mid_rate() {
            givenSyncIsEnabled();
            given(repository.countByCurrencyAndRateType("USD", FxRateType.MID)).willReturn(0L);

            service.ensureRates(List.of(USD));

            then(syncJob).should().run(List.of(USD));
        }

        @Test
        void bundles_all_missing_currencies_deduplicated_into_one_sync_run() {
            // One run, one lock acquisition: separate runs would let the first currency's
            // backfill hold the sync lock and turn every other one into a SKIPPED run.
            givenSyncIsEnabled();
            given(repository.countByCurrencyAndRateType("USD", FxRateType.MID)).willReturn(0L);
            given(repository.countByCurrencyAndRateType("EUR", FxRateType.MID)).willReturn(0L);

            service.ensureRates(List.of(USD, EUR, USD, EUR));

            then(syncJob).should(times(1)).run(List.of(USD, EUR));
        }

        @Test
        void syncs_only_the_currencies_that_have_no_mid_rate_yet() {
            givenSyncIsEnabled();
            given(repository.countByCurrencyAndRateType("USD", FxRateType.MID)).willReturn(0L);
            given(repository.countByCurrencyAndRateType("EUR", FxRateType.MID)).willReturn(400L);

            service.ensureRates(List.of(USD, EUR));

            then(syncJob).should().run(List.of(USD));
        }

        @Test
        void does_nothing_when_every_currency_already_has_a_mid_rate() {
            givenSyncIsEnabled();
            given(repository.countByCurrencyAndRateType("USD", FxRateType.MID)).willReturn(1L);

            service.ensureRates(List.of(USD));

            then(syncJob).should(never()).run(anyList());
        }

        @Test
        void ignores_chf_and_unknown_currencies_without_touching_anything() {
            service.ensureRates(Arrays.asList(CurrencyCode.CHF, null));
            service.ensureRates(List.of());

            then(syncJobProvider).shouldHaveNoInteractions();
            then(repository).shouldHaveNoInteractions();
        }

        @Test
        void stays_inert_when_sync_is_disabled() {
            given(syncJobProvider.getIfAvailable()).willReturn(null);

            service.ensureRates(List.of(USD));

            then(repository).shouldHaveNoInteractions();
        }

        @Test
        void swallows_a_failure_instead_of_letting_it_reach_the_caller() {
            givenSyncIsEnabled();
            given(repository.countByCurrencyAndRateType(anyString(), any()))
                    .willThrow(new IllegalStateException("db down"));

            assertThatNoException().isThrownBy(() -> service.ensureRates(List.of(USD)));
        }
    }
}
