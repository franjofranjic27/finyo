package ch.finyo.investment;

import ch.finyo.BaseIntegrationTest;
import ch.finyo.common.money.CurrencyCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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

    @BeforeEach
    void resetTable() {
        instrumentRepository.deleteAll();
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

    private Instrument instrument(String name, CurrencyCode currency) {
        return Instrument.builder()
                .userId(TEST_USER_ID)
                .name(name)
                .currency(currency)
                .build();
    }
}
