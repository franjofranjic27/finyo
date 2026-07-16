package ch.finyo.fx;

import ch.finyo.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for FxRateRepository against a real PostgreSQL 17.
 *
 * Testcontainers rather than a mock, because everything under test lives in the schema: the
 * {@code ON CONFLICT (currency, rate_date, rate_type)} upsert, the composite key that lets a mid
 * and an official rate for the same day coexist, and the "latest at or before a date" lookup the
 * converter depends on. A mock would verify my belief about SQL, not the SQL.
 */
@DisplayName("FxRateRepository (Postgres)")
class FxRateRepositoryIT extends BaseIntegrationTest {

    private static final String EUR = "EUR";
    private static final OffsetDateTime RETRIEVED_AT =
            OffsetDateTime.of(2026, 7, 14, 17, 15, 0, 0, ZoneOffset.UTC);

    @Autowired
    private FxRateRepository repository;

    @Autowired
    private FxRateWriter writer;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    /**
     * Writes through {@link FxRateWriter}, not the raw repository: the upsert is a {@code @Modifying}
     * native query and needs a transaction, which the writer supplies (REQUIRES_NEW) — the same path
     * production uses, so the ON CONFLICT is exercised exactly as it runs.
     */
    private void store(String currency, LocalDate date, String chfPerUnit, FxRateType type, String source) {
        writer.store(FxRate.builder()
                .currency(currency).rateDate(date).chfPerUnit(new BigDecimal(chfPerUnit))
                .rateType(type).source(source).retrievedAt(RETRIEVED_AT).build());
    }

    @Nested
    @DisplayName("the upsert")
    class Upsert {

        @Test
        void stores_a_rate_that_was_not_there_before() {
            store(EUR, LocalDate.of(2026, 7, 14), "0.9250", FxRateType.MID, "frankfurter");

            FxRate stored = repository
                    .findTopByCurrencyAndRateTypeAndRateDateLessThanEqualOrderByRateDateDesc(
                            EUR, FxRateType.MID, LocalDate.of(2026, 7, 14))
                    .orElseThrow();
            assertThat(stored.getChfPerUnit()).isEqualByComparingTo("0.9250");
            assertThat(stored.getSource()).isEqualTo("frankfurter");
            assertThat(stored.getRetrievedAt()).isEqualTo(RETRIEVED_AT);
        }

        @Test
        void writing_the_same_currency_day_and_type_twice_updates_instead_of_failing() {
            LocalDate day = LocalDate.of(2026, 7, 14);
            store(EUR, day, "0.9250", FxRateType.MID, "frankfurter");
            store(EUR, day, "0.9300", FxRateType.MID, "frankfurter");

            assertThat(repository.count()).isEqualTo(1);
            assertThat(repository.findTopByCurrencyAndRateTypeAndRateDateLessThanEqualOrderByRateDateDesc(
                    EUR, FxRateType.MID, day).orElseThrow().getChfPerUnit()).isEqualByComparingTo("0.9300");
        }

        @Test
        void a_mid_and_an_official_rate_for_the_same_day_coexist() {
            // The whole reason rate_type is in the primary key: the ECB mid rate and the BAZG
            // sell rate for the same EUR day are different facts and must not overwrite.
            LocalDate day = LocalDate.of(2026, 7, 14);
            store(EUR, day, "0.9257", FxRateType.MID, "frankfurter");
            store(EUR, day, "0.9366", FxRateType.OFFICIAL_CH, "bazg");

            assertThat(repository.count()).isEqualTo(2);
            assertThat(repository.findTopByCurrencyAndRateTypeAndRateDateLessThanEqualOrderByRateDateDesc(
                    EUR, FxRateType.MID, day).orElseThrow().getChfPerUnit()).isEqualByComparingTo("0.9257");
            assertThat(repository.findTopByCurrencyAndRateTypeAndRateDateLessThanEqualOrderByRateDateDesc(
                    EUR, FxRateType.OFFICIAL_CH, day).orElseThrow().getChfPerUnit()).isEqualByComparingTo("0.9366");
        }
    }

    @Nested
    @DisplayName("latest at or before a date — never interpolated")
    class LatestAtOrBefore {

        @Test
        void returns_the_most_recent_rate_on_a_day_that_has_none_of_its_own() {
            // A Saturday has no rate. The converter asks for it and must get Friday's, not nothing
            // and not a value invented to fill the gap.
            store(EUR, LocalDate.of(2026, 7, 10), "0.9250", FxRateType.MID, "frankfurter"); // Friday
            store(EUR, LocalDate.of(2026, 7, 13), "0.9280", FxRateType.MID, "frankfurter"); // Monday

            FxRate onSaturday = repository
                    .findTopByCurrencyAndRateTypeAndRateDateLessThanEqualOrderByRateDateDesc(
                            EUR, FxRateType.MID, LocalDate.of(2026, 7, 11))
                    .orElseThrow();

            assertThat(onSaturday.getRateDate()).isEqualTo(LocalDate.of(2026, 7, 10));
            assertThat(onSaturday.getChfPerUnit()).isEqualByComparingTo("0.9250");
        }

        @Test
        void is_empty_when_the_currency_has_no_rate_at_or_before_the_date() {
            store(EUR, LocalDate.of(2026, 7, 14), "0.9250", FxRateType.MID, "frankfurter");

            assertThat(repository.findTopByCurrencyAndRateTypeAndRateDateLessThanEqualOrderByRateDateDesc(
                    EUR, FxRateType.MID, LocalDate.of(2026, 7, 1))).isEmpty();
            assertThat(repository.findTopByCurrencyAndRateTypeAndRateDateLessThanEqualOrderByRateDateDesc(
                    "GBP", FxRateType.MID, LocalDate.of(2026, 7, 14))).isEmpty();
        }
    }

    @Nested
    @DisplayName("countByCurrencyAndRateType — the backfill decision")
    class Count {

        @Test
        void counts_only_the_rows_of_the_asked_currency_and_type() {
            store(EUR, LocalDate.of(2026, 7, 13), "0.9250", FxRateType.MID, "frankfurter");
            store(EUR, LocalDate.of(2026, 7, 14), "0.9280", FxRateType.MID, "frankfurter");
            store(EUR, LocalDate.of(2026, 7, 14), "0.9366", FxRateType.OFFICIAL_CH, "bazg");

            assertThat(repository.countByCurrencyAndRateType(EUR, FxRateType.MID)).isEqualTo(2);
            assertThat(repository.countByCurrencyAndRateType(EUR, FxRateType.OFFICIAL_CH)).isEqualTo(1);
            assertThat(repository.countByCurrencyAndRateType("GBP", FxRateType.MID)).isZero();
        }
    }
}
