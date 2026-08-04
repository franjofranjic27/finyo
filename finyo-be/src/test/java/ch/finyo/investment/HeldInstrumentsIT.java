package ch.finyo.investment;

import ch.finyo.BaseIntegrationTest;
import ch.finyo.common.money.CurrencyCode;
import ch.finyo.marketdata.InstrumentPrice;
import ch.finyo.marketdata.InstrumentPriceRepository;
import ch.finyo.marketdata.spi.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the query behind the nightly FX sync against a real database.
 *
 * It is an IT and not a unit test on purpose: every caller reaches this through
 * {@code HeldCurrenciesQuery}, which the sync job's test mocks, so a query that no Hibernate could
 * parse once shipped green through the whole suite and only failed at runtime.
 */
class HeldInstrumentsIT extends BaseIntegrationTest {

    @Autowired
    private HeldInstruments heldInstruments;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private InstrumentPriceRepository priceRepository;

    @BeforeEach
    void resetTable() {
        instrumentRepository.deleteAll();
        priceRepository.deleteAll();
    }

    @Test
    void findsEachForeignCurrencyOnce() {
        instrumentRepository.save(instrument("USD ETF", new CurrencyCode("USD")));
        instrumentRepository.save(instrument("Another USD holding", new CurrencyCode("USD")));
        instrumentRepository.save(instrument("EUR fund", new CurrencyCode("EUR")));

        assertThat(heldInstruments.findForeign())
                .containsExactlyInAnyOrder(new CurrencyCode("USD"), new CurrencyCode("EUR"));
    }

    @Test
    void skipsChfBecauseItNeedsNoRate() {
        instrumentRepository.save(instrument("Swiss fund", CurrencyCode.CHF));

        assertThat(heldInstruments.findForeign()).isEmpty();
    }

    /** Null means "we do not know" — an instrument resolved through OpenFIGI has no currency. */
    @Test
    void skipsInstrumentsWithoutAKnownCurrency() {
        instrumentRepository.save(instrument("Resolved by OpenFIGI", null));

        assertThat(heldInstruments.findForeign()).isEmpty();
    }

    /**
     * The production case behind the fix: an instrument imported as CHF whose market quotes
     * arrive in USD is valued in USD, so the sync scope must contain USD even though no
     * instrument row says so.
     */
    @Test
    void includesTheQuoteCurrencyOfAHeldIsin() {
        instrumentRepository.save(instrument("Imported as CHF", CurrencyCode.CHF).toBuilder()
                .isin("IE00B0M62Q58")
                .build());
        priceRepository.save(InstrumentPrice.builder()
                .isin("IE00B0M62Q58")
                .priceDate(LocalDate.of(2026, 8, 3))
                .close(new BigDecimal("102.38"))
                .currency(new CurrencyCode("USD"))
                .source(DataSource.SIX)
                .retrievedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());

        assertThat(heldInstruments.findForeign()).containsExactly(new CurrencyCode("USD"));
    }

    /** A quote currency of an ISIN nobody holds must not widen the sync scope. */
    @Test
    void ignoresQuoteCurrenciesOfUnheldIsins() {
        instrumentRepository.save(instrument("Swiss fund", CurrencyCode.CHF));
        priceRepository.save(InstrumentPrice.builder()
                .isin("US0000000000")
                .priceDate(LocalDate.of(2026, 8, 3))
                .close(BigDecimal.ONE)
                .currency(new CurrencyCode("USD"))
                .source(DataSource.SIX)
                .retrievedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());

        assertThat(heldInstruments.findForeign()).isEmpty();
    }

    private Instrument instrument(String name, CurrencyCode currency) {
        return Instrument.builder()
                .userId(TEST_USER_ID)
                .name(name)
                .currency(currency)
                .build();
    }
}
