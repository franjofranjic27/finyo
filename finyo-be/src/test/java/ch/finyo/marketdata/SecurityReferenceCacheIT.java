package ch.finyo.marketdata;

import ch.finyo.BaseIntegrationTest;
import ch.finyo.common.money.CurrencyCode;
import ch.finyo.marketdata.spi.DataSource;
import ch.finyo.marketdata.spi.SecurityReference;
import ch.finyo.marketdata.spi.SecurityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Integration tests for SecurityReferenceCache against a real PostgreSQL 17.
 *
 * Testcontainers rather than a unit test with a mocked repository, because every
 * property under test lives in the database and nowhere else:
 *
 * <ul>
 *   <li>{@code ON CONFLICT (isin) DO UPDATE} only exists in the native query — a mocked
 *       repository would happily "verify" an upsert that Postgres would reject. The race
 *       it defends against is real: two imports of the same new ISIN both miss the cache
 *       and both insert, and with {@code save()} one of them hits the primary key and the
 *       user's perfectly valid position create dies with a 409 over a write that was pure
 *       optimisation.</li>
 *   <li>The column widths are the schema's, so whether an over-long vendor string fails
 *       the write is a question only Postgres can answer.</li>
 * </ul>
 *
 * The governing rule, and what most of these tests assert: <b>a cache write must never
 * be able to fail the user's operation.</b> The caller already has its answer; the row is
 * an optimisation.
 */
@DisplayName("SecurityReferenceCache (Postgres)")
class SecurityReferenceCacheIT extends BaseIntegrationTest {

    private static final String ISIN = "IE00B4L5Y983";

    private static final OffsetDateTime RETRIEVED_AT =
            OffsetDateTime.of(2026, 7, 14, 10, 30, 0, 0, ZoneOffset.UTC);

    @Autowired
    private SecurityReferenceCache cache;

    @Autowired
    private SecurityReferenceRepository repository;

    @BeforeEach
    void cleanTable() {
        repository.deleteAll();
    }

    private static SecurityReference reference(String name, CurrencyCode currency, DataSource source) {
        return new SecurityReference(ISIN, "24476758", "SWDA", name,
                SecurityType.ETF, currency, "BlackRock", source, RETRIEVED_AT);
    }

    // =========================================================================
    // The upsert
    // =========================================================================

    @Test
    void stores_a_reference_that_was_not_cached_before() {
        cache.store(reference("ISHARES CORE MSCI WORLD", new CurrencyCode("USD"), DataSource.SIX));

        CachedSecurityReference stored = repository.findById(ISIN).orElseThrow();
        assertThat(stored.getName()).isEqualTo("ISHARES CORE MSCI WORLD");
        assertThat(stored.getCurrency()).isEqualTo(new CurrencyCode("USD"));
        assertThat(stored.getSource()).isEqualTo(DataSource.SIX);
    }

    @Test
    void storing_the_same_isin_twice_updates_instead_of_failing_on_the_primary_key() {
        // The concurrency case, played out sequentially: the second write is what a losing
        // racer does. ON CONFLICT makes it a non-event — and it has to, because the second
        // writer is somebody's position create, and a 409 for them would be absurd.
        cache.store(reference("ISHARES CORE MSCI WORLD", new CurrencyCode("USD"), DataSource.SIX));

        assertThatCode(() -> cache.store(
                reference("ISHARES CORE MSCI WORLD", new CurrencyCode("USD"), DataSource.SIX)))
                .doesNotThrowAnyException();

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void a_second_write_replaces_the_cached_values_with_the_fresher_ones() {
        // The cache is a cache, not an archive: when a provider answers again, its answer
        // wins. Otherwise a reference first resolved through OpenFIGI (no currency) could
        // never be upgraded by a later SIX hit that knows it is USD.
        cache.store(reference("ISHARES CORE MSCI WORLD", null, DataSource.OPENFIGI));

        cache.store(reference("iShares Core MSCI World UCITS ETF", new CurrencyCode("USD"), DataSource.SIX));

        CachedSecurityReference stored = repository.findById(ISIN).orElseThrow();
        assertThat(stored.getName()).isEqualTo("iShares Core MSCI World UCITS ETF");
        assertThat(stored.getCurrency()).isEqualTo(new CurrencyCode("USD"));
        assertThat(stored.getSource()).isEqualTo(DataSource.SIX);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void an_update_may_take_a_currency_back_to_unknown() {
        // NULL is a value here, not an absence of one. If a later lookup genuinely carries
        // no currency, overwriting USD with NULL is honest — pretending we still know is
        // not, and EXCLUDED.currency is what makes the column behave that way.
        cache.store(reference("ISHARES CORE MSCI WORLD", new CurrencyCode("USD"), DataSource.SIX));

        cache.store(reference("ISHARES CORE MSCI WORLD", null, DataSource.OPENFIGI));

        assertThat(repository.findById(ISIN).orElseThrow().getCurrency()).isNull();
    }

    // =========================================================================
    // A cache write never fails the caller
    // =========================================================================

    @Test
    void does_not_store_a_reference_without_an_isin_and_does_not_complain_about_it() {
        // No ISIN, no primary key. The reference is still a perfectly usable answer for
        // the caller — it just cannot be cached, and that must not turn into an exception
        // on the user's request path.
        SecurityReference withoutIsin = new SecurityReference(
                null, "24476758", "SWDA", "ISHARES CORE MSCI WORLD", SecurityType.ETF,
                CurrencyCode.CHF, "BlackRock", DataSource.SIX, RETRIEVED_AT);

        assertThatCode(() -> cache.store(withoutIsin)).doesNotThrowAnyException();

        assertThat(repository.count()).isZero();
    }

    @Test
    void truncates_a_vendor_string_that_is_longer_than_the_column() {
        // Vendors are not bound by our column widths. A 300-character issuer name is not a
        // reason to fail a position create.
        String tooLong = "X".repeat(300);
        SecurityReference verbose = new SecurityReference(
                ISIN, "24476758", "SWDA", tooLong, SecurityType.ETF,
                CurrencyCode.CHF, tooLong, DataSource.SIX, RETRIEVED_AT);

        cache.store(verbose);

        CachedSecurityReference stored = repository.findById(ISIN).orElseThrow();
        assertThat(stored.getName()).hasSize(255);
        assertThat(stored.getIssuer()).hasSize(255);
    }

    @Test
    void swallows_a_write_the_schema_cannot_hold_rather_than_failing_the_caller() {
        // A ticker column is VARCHAR(20) and nothing truncates it — a vendor sending
        // something longer makes Postgres reject the row. The caller must never notice:
        // they already have the reference in hand, and the cache is an optimisation.
        // REQUIRES_NEW is what contains the rollback to this write alone.
        SecurityReference unstorable = new SecurityReference(
                ISIN, "24476758", "T".repeat(50), "ISHARES CORE MSCI WORLD", SecurityType.ETF,
                CurrencyCode.CHF, "BlackRock", DataSource.SIX, RETRIEVED_AT);

        assertThatCode(() -> cache.store(unstorable)).doesNotThrowAnyException();

        assertThat(repository.findById(ISIN)).isEmpty();
    }
}
