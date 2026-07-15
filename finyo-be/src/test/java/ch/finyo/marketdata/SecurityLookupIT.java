package ch.finyo.marketdata;

import ch.finyo.BaseIntegrationTest;
import ch.finyo.common.money.CurrencyCode;
import ch.finyo.marketdata.spi.DataSource;
import ch.finyo.common.SourceResult;
import ch.finyo.marketdata.spi.SecurityId;
import ch.finyo.marketdata.spi.SecurityReference;
import ch.finyo.marketdata.spi.SecurityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static ch.finyo.common.SourceResults.foundValue;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the security reference cache against a real PostgreSQL 17.
 *
 * Two things can only be verified here, and both are silent killers:
 *
 *   1. Migration V33 and the JPA mapping agree. They did not: the migration declared
 *      CHAR(3) while CurrencyCodeConverter maps CurrencyCode to a String — i.e.
 *      VARCHAR — and ddl-auto=validate refused to start the application at all. A unit
 *      test cannot see that; the schema is not in the picture.
 *   2. CurrencyCodeConverter survives a real round trip through the database, so a USD
 *      ETF is still a USD ETF after a restart — and a NULL currency is still NULL,
 *      rather than being converted into a plausible-looking CHF on the way back.
 *
 * The test profile configures reference-providers: [] — no adapter bean exists and no
 * HTTP call can happen. That is not a limitation of this test, it is one of its
 * subjects: the cache alone must still answer.
 */
@DisplayName("Security reference cache (Postgres)")
class SecurityLookupIT extends BaseIntegrationTest {

    private static final String ISIN = "IE00B4L5Y983";
    private static final String VALOR = "24476758";
    private static final String TICKER = "SWDA";

    /** Whole seconds: TIMESTAMPTZ keeps microseconds, OffsetDateTime.now() nanoseconds. */
    private static final OffsetDateTime RETRIEVED_AT =
            OffsetDateTime.of(2026, 7, 14, 10, 30, 0, 0, ZoneOffset.UTC);

    @Autowired
    private SecurityReferenceRepository repository;

    @Autowired
    private SecurityReferenceCache cache;

    @Autowired
    private SecurityLookup securityLookup;

    @BeforeEach
    void cleanTable() {
        repository.deleteAll();
    }

    private static SecurityReference reference(CurrencyCode currency) {
        return new SecurityReference(ISIN, VALOR, TICKER, "ISHARES CORE MSCI WORLD",
                SecurityType.ETF, currency, "BlackRock", DataSource.OPENFIGI, RETRIEVED_AT);
    }

    // =========================================================================
    // Schema and mapping
    // =========================================================================

    @Test
    void persists_a_reference_and_reads_every_column_back_unchanged() {
        cache.store(reference(new CurrencyCode("USD")));

        // The cache writes in its own REQUIRES_NEW transaction, so this is a real SELECT
        // and not a persistence context handing back the object we just put in.
        SecurityReference read = repository.findById(ISIN).orElseThrow().toReference();

        assertThat(read.isin()).isEqualTo(ISIN);
        assertThat(read.valor()).isEqualTo(VALOR);
        assertThat(read.ticker()).isEqualTo(TICKER);
        assertThat(read.name()).isEqualTo("ISHARES CORE MSCI WORLD");
        assertThat(read.type()).isEqualTo(SecurityType.ETF);
        assertThat(read.issuer()).isEqualTo("BlackRock");
        assertThat(read.source()).isEqualTo(DataSource.OPENFIGI);
        assertThat(read.retrievedAt()).isEqualTo(RETRIEVED_AT);
    }

    @Test
    void round_trips_a_foreign_currency_through_the_attribute_converter() {
        // The column this whole PR exists for: without it a USD ETF was summed into the
        // portfolio total as though it were CHF, and the total was simply wrong.
        cache.store(reference(new CurrencyCode("USD")));

        CurrencyCode currency = repository.findById(ISIN).orElseThrow().getCurrency();

        assertThat(currency).isEqualTo(new CurrencyCode("USD"));
        assertThat(currency.isChf()).isFalse();
    }

    @Test
    void round_trips_a_reference_without_a_currency() {
        // OpenFIGI never returns one — a NULL currency has to survive the trip as NULL
        // rather than blowing up the converter on the way back, and rather than quietly
        // becoming CHF, which is the whole distinction the schema is nullable for.
        cache.store(reference(null));

        assertThat(repository.findById(ISIN).orElseThrow().getCurrency()).isNull();
    }

    // =========================================================================
    // The indexed lookup paths
    // =========================================================================

    @Test
    void finds_a_cached_reference_by_valor() {
        // The valor is the Swiss entry point — it is what a bank statement shows, and SIX
        // is the only free provider that resolves one.
        cache.store(reference(CurrencyCode.CHF));

        assertThat(repository.findFirstByValor(VALOR)).isPresent();
    }

    // =========================================================================
    // SecurityLookup on top of the cache, with no provider configured
    // =========================================================================

    @Test
    void resolves_a_cached_security_without_any_provider_in_the_chain() {
        // The availability property, end to end: every provider is off (as it would be
        // the day SIX has to be switched off for licensing reasons) and the lookup
        // still answers from Postgres.
        cache.store(reference(new CurrencyCode("USD")));

        SecurityReference resolved = foundValue(securityLookup.resolve(new SecurityId.Isin(ISIN)));

        assertThat(resolved.currency()).isEqualTo(new CurrencyCode("USD"));
        assertThat(resolved.source()).isEqualTo(DataSource.OPENFIGI);
    }

    @Test
    void resolves_a_cached_security_by_a_lowercase_isin() {
        // SecurityId.Isin uppercases on construction and the ISIN is the primary key, so
        // a hand-typed ISIN hits the PK index rather than missing the cache and triggering
        // a vendor call.
        cache.store(reference(CurrencyCode.CHF));

        assertThat(securityLookup.resolve(new SecurityId.Isin("ie00b4l5y983")))
                .isInstanceOf(SourceResult.Found.class);
    }

    @Test
    void reports_NotFound_for_an_unknown_security_when_no_provider_is_configured() {
        // Nobody was asked, but nobody was unreachable either — so NotFound, not
        // Unavailable. An empty chain is a deliberate configuration, not an outage.
        SourceResult<SecurityReference> resolved = securityLookup.resolve(new SecurityId.Isin("CH9999999999"));

        assertThat(resolved).isEqualTo(SourceResult.notFound());
    }
}
