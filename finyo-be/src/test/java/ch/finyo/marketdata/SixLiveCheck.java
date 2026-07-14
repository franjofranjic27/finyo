package ch.finyo.marketdata;

import ch.finyo.BaseIntegrationTest;
import ch.finyo.marketdata.spi.DataSource;
import ch.finyo.marketdata.spi.SecurityId;
import ch.finyo.marketdata.spi.SecurityReference;
import ch.finyo.marketdata.spi.SecurityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static ch.finyo.marketdata.spi.LookupResults.foundReference;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hits the real SIX and OpenFIGI endpoints. Deliberately named so that neither
 * Surefire (*Test) nor Failsafe (*IT) picks it up — it must never run in CI, where
 * it would be slow, flaky, dependent on the network and rude to an endpoint finyo
 * is only tolerated on.
 *
 * Run it by hand when the adapters change:
 * <pre>./mvnw test -Dtest=SixLiveCheck -DfailIfNoTests=false</pre>
 *
 * Its purpose is the one thing mocked tests cannot establish: that the fixtures the
 * unit tests are built on still match what the vendors actually return. Both sources
 * are unofficial and may change their payload without notice — this is how that gets
 * noticed deliberately rather than in production.
 */
@TestPropertySource(properties = {
        "finyo.marketdata.reference-providers=six,openfigi",
        "finyo.marketdata.six.enabled=true",
        "finyo.marketdata.openfigi.enabled=true"
})
class SixLiveCheck extends BaseIntegrationTest {

    @Autowired
    private SecurityLookup securityLookup;

    @Autowired
    private SecurityReferenceRepository repository;

    @Test
    @DisplayName("resolves Nestlé by its Swiss valor number — the case no other free source covers")
    void resolvesByValor() {
        SecurityReference nestle = foundReference(securityLookup.resolve(new SecurityId.Valor("3886335")));

        assertThat(nestle.isin()).isEqualTo("CH0038863350");
        assertThat(nestle.ticker()).isEqualTo("NESN");
        assertThat(nestle.name()).containsIgnoringCase("nestle");
        assertThat(nestle.type()).isEqualTo(SecurityType.EQUITY);
        assertThat(nestle.currency().value()).isEqualTo("CHF");
        assertThat(nestle.source()).isEqualTo(DataSource.SIX);

        // and it is now in Postgres, which is what keeps the lookup alive when SIX is not
        assertThat(repository.findById("CH0038863350")).isPresent();
    }

    @Test
    @DisplayName("resolves a USD-quoted ETF by ISIN, currency included — the field FX depends on")
    void resolvesForeignCurrencyEtf() {
        SecurityReference etf = foundReference(securityLookup.resolve(new SecurityId.Isin("IE00B4L5Y983")));

        assertThat(etf.ticker()).isEqualTo("SWDA");
        assertThat(etf.type()).isEqualTo(SecurityType.ETF);
        // The whole point of V33: this instrument is NOT in CHF, and until now the
        // portfolio summed it as if it were.
        assertThat(etf.currency().value()).isEqualTo("USD");
    }

    @Test
    @DisplayName("falls through to OpenFIGI for an unlisted 3a fund that SIX does not know")
    void fallsBackToOpenFigiForUnlistedPillar3Fund() {
        // CSIF (CH) Equity World ex CH — a VIAC/finpension building block. It is an
        // unlisted institutional share class, so SIX answers totalRows: 0. If this ever
        // comes back NotFound, the 3a instruments regress to name-guessing.
        SecurityReference fund = foundReference(securityLookup.resolve(new SecurityId.Isin("CH0214967314")));

        assertThat(fund.source()).isEqualTo(DataSource.OPENFIGI);
        assertThat(fund.name()).isNotBlank();
        // OpenFIGI is symbology, not market data — it carries no currency. Honest gap, and
        // the reason Instrument.currency has to stay nullable.
        assertThat(fund.currency()).isNull();
    }
}
