package ch.finyo.marketdata;

import ch.finyo.BaseIntegrationTest;
import ch.finyo.common.money.CurrencyCode;
import ch.finyo.marketdata.spi.DataSource;
import ch.finyo.marketdata.spi.Quote;
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
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Integration tests for InstrumentPriceWriter against a real PostgreSQL 17.
 *
 * Testcontainers rather than a mocked repository, because every property under test lives in
 * the database and nowhere else:
 *
 * <ul>
 *   <li>{@code ON CONFLICT (isin, price_date) DO UPDATE} exists only in the native query. A
 *       mocked repository would happily "verify" an upsert that Postgres would reject — and
 *       the collision is not hypothetical: a manual admin trigger crossing the nightly run
 *       writes the same ISIN and day twice.</li>
 *   <li>The column widths are the schema's, so whether an over-long vendor string fails the
 *       write is a question only Postgres can answer.</li>
 * </ul>
 *
 * The governing rule, and what most of these tests assert: <b>storing a price must never be
 * able to fail the operation that triggered it.</b> Creating a position prices the new holding
 * straight away; the position is the point, the price is a convenience.
 */
@DisplayName("InstrumentPriceWriter (Postgres)")
class InstrumentPriceWriterIT extends BaseIntegrationTest {

    private static final String ISIN = "CH0038863350";
    private static final LocalDate TRADE_DAY = LocalDate.of(2026, 7, 14);

    /** Whole seconds: TIMESTAMPTZ keeps microseconds, OffsetDateTime.now() nanoseconds. */
    private static final OffsetDateTime RETRIEVED_AT =
            OffsetDateTime.of(2026, 7, 14, 22, 30, 0, 0, ZoneOffset.UTC);

    @Autowired
    private InstrumentPriceWriter writer;

    @Autowired
    private InstrumentPriceRepository repository;

    @BeforeEach
    void cleanTable() {
        repository.deleteAll();
    }

    private static Quote quote(String isin, String price, CurrencyCode currency, LocalDate asOf) {
        return new Quote(isin, price == null ? null : new BigDecimal(price), currency, asOf,
                RETRIEVED_AT, true, DataSource.SIX);
    }

    private static Quote nestle(String price) {
        return quote(ISIN, price, CurrencyCode.CHF, TRADE_DAY);
    }

    // =========================================================================
    // The upsert
    // =========================================================================

    @Nested
    @DisplayName("the upsert")
    class Upsert {

        @Test
        void stores_a_price_that_was_not_there_before() {
            writer.store(nestle("83.88"));

            // The writer commits in its own REQUIRES_NEW transaction, so this is a real SELECT
            // and not a persistence context handing back what we just put in.
            InstrumentPrice stored = repository.findFirstByIsinOrderByPriceDateDesc(ISIN).orElseThrow();
            assertThat(stored.getClose()).isEqualByComparingTo("83.88");
            assertThat(stored.getCurrency()).isEqualTo(CurrencyCode.CHF);
            assertThat(stored.getPriceDate()).isEqualTo(TRADE_DAY);
            assertThat(stored.getSource()).isEqualTo(DataSource.SIX);
            assertThat(stored.getRetrievedAt()).isEqualTo(RETRIEVED_AT);
        }

        @Test
        void writing_the_same_security_and_day_twice_updates_instead_of_failing_on_the_primary_key() {
            // The nightly sync racing a manual admin trigger, or a re-run after a partial
            // failure. The price is a fact about the market: the later read of it simply wins,
            // and a crash here would take down whichever caller lost the race.
            writer.store(nestle("83.88"));

            assertThatCode(() -> writer.store(nestle("84.10"))).doesNotThrowAnyException();

            assertThat(repository.count()).isEqualTo(1);
            assertThat(repository.findFirstByIsinOrderByPriceDateDesc(ISIN).orElseThrow().getClose())
                    .isEqualByComparingTo("84.10");
        }

        @Test
        void keeps_one_row_per_trading_day_so_the_table_is_a_time_series() {
            // This is what portfolio_snapshot is not, and why the two are complementary rather
            // than redundant: this is a market fact, a snapshot is a user fact.
            writer.store(quote(ISIN, "83.88", CurrencyCode.CHF, TRADE_DAY));
            writer.store(quote(ISIN, "84.55", CurrencyCode.CHF, TRADE_DAY.plusDays(1)));

            assertThat(repository.count()).isEqualTo(2);
            assertThat(repository.findFirstByIsinOrderByPriceDateDesc(ISIN).orElseThrow().getClose())
                    .isEqualByComparingTo("84.55");
        }

        @Test
        void round_trips_a_foreign_currency_rather_than_flattening_it_to_francs() {
            // The column the portfolio total depends on: a USD close summed as CHF is simply
            // a wrong number, and the schema is what keeps the two apart.
            writer.store(quote("IE00B4L5Y983", "144.20", new CurrencyCode("USD"), TRADE_DAY));

            InstrumentPrice stored =
                    repository.findFirstByIsinOrderByPriceDateDesc("IE00B4L5Y983").orElseThrow();
            assertThat(stored.getCurrency()).isEqualTo(new CurrencyCode("USD"));
            assertThat(stored.getCurrency().isChf()).isFalse();
        }
    }

    // =========================================================================
    // What must never be written
    // =========================================================================

    @Nested
    @DisplayName("refuses a price that would mean nothing")
    class RefusesMeaninglessPrices {

        @Test
        void does_not_store_a_quote_without_a_currency() {
            // A price without its currency is a number without a meaning. Storing 144.20 with
            // no currency and summing it into a CHF total is the original bug in miniature —
            // and defaulting it to CHF would be that bug exactly.
            writer.store(quote(ISIN, "83.88", null, TRADE_DAY));

            assertThat(repository.count()).isZero();
        }

        @Test
        void does_not_store_a_quote_without_a_trading_day() {
            // The row is keyed on (isin, price_date). A price with no day cannot be filed in a
            // time series at all — and filing it under today would invent a close that never
            // happened, which is worse than having none.
            writer.store(quote(ISIN, "83.88", CurrencyCode.CHF, null));

            assertThat(repository.count()).isZero();
        }

        @Test
        void does_not_store_a_quote_whose_price_is_zero() {
            // SIX returns 0 for a listed-but-never-traded instrument. The adapter already turns
            // that into "no quote", so this is the writer's own second line: a 0 filed as a
            // close becomes a value of 0 and a −100% return that looks entirely real. close is
            // NOT NULL but a zero would satisfy the column — only this guard rejects it.
            writer.store(nestle("0"));

            assertThat(repository.count()).isZero();
        }

        @Test
        void does_not_store_a_quote_whose_price_is_negative() {
            // No instrument trades below zero; a negative close is a vendor glitch, not a price.
            writer.store(nestle("-5"));

            assertThat(repository.count()).isZero();
        }

        @Test
        void refusing_a_meaningless_quote_is_not_an_error_for_the_caller() {
            // The refusal is the writer's business, not the position create's. It logs and
            // moves on; nobody upstream learns about it.
            assertThatCode(() -> writer.store(quote(ISIN, "83.88", null, null)))
                    .doesNotThrowAnyException();
        }
    }

    // =========================================================================
    // A failed write never reaches the caller
    // =========================================================================

    @Nested
    @DisplayName("a write the database rejects never reaches the caller")
    class FailuresAreContained {

        @Test
        void swallows_a_write_the_schema_cannot_hold_rather_than_failing_the_user() {
            // isin is VARCHAR(12) and nothing truncates it. This is what a position create
            // looks like when the price write goes wrong: the user's position is the point,
            // and it must not die over a cached price. REQUIRES_NEW is what contains the
            // rollback to this write alone — with @Transactional the catch block would sit
            // inside the transaction boundary and UnexpectedRollbackException would escape
            // past it on commit.
            Quote unstorable = quote("X".repeat(20), "83.88", CurrencyCode.CHF, TRADE_DAY);

            assertThatCode(() -> writer.store(unstorable)).doesNotThrowAnyException();

            assertThat(repository.count()).isZero();
        }

        @Test
        void swallows_a_quote_with_no_price_at_all() {
            // close is NOT NULL. The adapter already reports a missing price as NotFound, so
            // this should not happen — and if a future provider gets it wrong, the nightly job
            // must not go down with it.
            Quote priceless = quote(ISIN, null, CurrencyCode.CHF, TRADE_DAY);

            assertThatCode(() -> writer.store(priceless)).doesNotThrowAnyException();

            assertThat(repository.count()).isZero();
        }
    }

    // =========================================================================
    // What the portfolio actually reads
    // =========================================================================

    @Test
    void findLatestForEach_returns_the_newest_row_per_security_in_a_single_query() {
        // The portfolio read path. An N+1 here would be one query per position on every page
        // load — and the correlated subquery is exactly the kind of thing that works against a
        // mock and not against Postgres.
        writer.store(quote(ISIN, "80.00", CurrencyCode.CHF, TRADE_DAY.minusDays(2)));
        writer.store(quote(ISIN, "83.88", CurrencyCode.CHF, TRADE_DAY));
        writer.store(quote("IE00B4L5Y983", "144.20", new CurrencyCode("USD"), TRADE_DAY.minusDays(1)));

        var latest = repository.findLatestForEach(java.util.List.of(ISIN, "IE00B4L5Y983"));

        assertThat(latest).hasSize(2);
        assertThat(latest)
                .filteredOn(price -> price.getIsin().equals(ISIN))
                .singleElement()
                .satisfies(price -> {
                    assertThat(price.getClose()).isEqualByComparingTo("83.88");
                    assertThat(price.getPriceDate()).isEqualTo(TRADE_DAY);
                });
    }

    @Test
    void findLatestForEach_says_nothing_about_a_security_nobody_has_priced() {
        writer.store(nestle("83.88"));

        var latest = repository.findLatestForEach(java.util.List.of(ISIN, "CH0214967314"));

        assertThat(latest).hasSize(1);
        assertThat(latest.getFirst().getIsin()).isEqualTo(ISIN);
    }
}
