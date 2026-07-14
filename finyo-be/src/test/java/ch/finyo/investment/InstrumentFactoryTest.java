package ch.finyo.investment;

import ch.finyo.common.money.CurrencyCode;
import ch.finyo.marketdata.SecurityLookup;
import ch.finyo.marketdata.spi.DataSource;
import ch.finyo.marketdata.spi.LookupResult;
import ch.finyo.marketdata.spi.SecurityId;
import ch.finyo.marketdata.spi.SecurityReference;
import ch.finyo.marketdata.spi.SecurityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * Unit tests for InstrumentFactory.
 *
 * The factory is where provider data and user input meet, and every test here is about
 * who wins that argument:
 *
 *   1. A resolved security replaces guesswork — type, currency, ticker and source all
 *      come from the provider instead of from a substring match on the name.
 *   2. Except the name itself: a user who typed one keeps it.
 *   3. When nobody knows the security — the normal case for the unlisted 3a funds this
 *      project cares most about — the old heuristic still runs, but the result is
 *      labelled HEURISTIC rather than dressed up as fact.
 *   4. And when nobody could be *reached*, the result is UNRESOLVED, which is a to-do
 *      rather than an answer. That distinction is the whole point: HEURISTIC is final
 *      and never re-asked, so recording an outage as HEURISTIC would freeze a guess
 *      into the database permanently.
 *
 * The rule that ties it together: an unknown currency stays null. Never CHF. A USD ETF
 * resolved through OpenFIGI (which publishes no currency at all) defaulted to CHF is the
 * original bug — the portfolio total summing dollars as francs — rebuilt one layer up.
 *
 * SecurityLookup is mocked: whether a provider answers is its own test's subject.
 */
@DisplayName("InstrumentFactory")
@ExtendWith(MockitoExtension.class)
class InstrumentFactoryTest {

    private static final String USER_ID = "user-factory-1";
    private static final String ISIN = "IE00B4L5Y983";
    private static final String VALOR = "24476758";

    private static final OffsetDateTime RETRIEVED_AT =
            OffsetDateTime.of(2026, 7, 14, 10, 30, 0, 0, ZoneOffset.UTC);

    @Mock
    private SecurityLookup securityLookup;

    @InjectMocks
    private InstrumentFactory instrumentFactory;

    @Captor
    private ArgumentCaptor<SecurityId> identifier;

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private static SecurityReference reference(SecurityType type, CurrencyCode currency, String name) {
        return new SecurityReference(ISIN, VALOR, "SWDA", name, type, currency,
                "BlackRock", DataSource.SIX, RETRIEVED_AT);
    }

    private static LookupResult found(SecurityType type, CurrencyCode currency, String name) {
        return LookupResult.found(reference(type, currency, name));
    }

    // =========================================================================
    // lookup(): which identifier goes to the providers
    // =========================================================================

    @Nested
    @DisplayName("lookup — choosing the identifier")
    class Lookup {

        @Test
        void looks_the_security_up_by_isin_when_one_is_given() {
            // ISIN is the better key: every provider resolves it, while only SIX knows
            // valor numbers.
            given(securityLookup.resolve(any())).willReturn(LookupResult.notFound());

            instrumentFactory.lookup(ISIN, VALOR);

            then(securityLookup).should().resolve(identifier.capture());
            assertThat(identifier.getValue()).isEqualTo(new SecurityId.Isin(ISIN));
        }

        @Test
        void looks_the_security_up_by_valor_when_no_isin_is_given() {
            given(securityLookup.resolve(any())).willReturn(LookupResult.notFound());

            instrumentFactory.lookup(null, VALOR);

            then(securityLookup).should().resolve(identifier.capture());
            assertThat(identifier.getValue()).isEqualTo(new SecurityId.Valor(VALOR));
        }

        @Test
        void hands_the_providers_answer_straight_through() {
            LookupResult unavailable = LookupResult.unavailable("six: read timed out");
            given(securityLookup.resolve(any())).willReturn(unavailable);

            assertThat(instrumentFactory.lookup(ISIN, null)).isEqualTo(unavailable);
        }

        @Test
        void never_looks_anything_up_when_neither_isin_nor_valor_is_given() {
            // A position entered by name only ("Notgroschen") has nothing to resolve.
            // Calling a rate-limited vendor with nothing to ask about is pure waste.
            LookupResult result = instrumentFactory.lookup(null, "  ");

            assertThat(result).isEqualTo(LookupResult.notFound());
            then(securityLookup).shouldHaveNoInteractions();
        }

        @Test
        void never_looks_up_an_identifier_that_is_not_a_well_formed_isin() {
            // The bulk path bypasses Bean Validation, so a CSV cell holding a 500-character
            // string used to travel all the way to OpenFIGI. It is refused here instead —
            // and NotFound, not Unavailable: nothing is wrong with the providers, the
            // identifier is simply not one, and retrying it forever would be pointless.
            LookupResult result = instrumentFactory.lookup("not-an-isin", null);

            assertThat(result).isEqualTo(LookupResult.notFound());
            then(securityLookup).shouldHaveNoInteractions();
        }
    }

    // =========================================================================
    // create(): a resolved security replaces the heuristic
    // =========================================================================

    @Nested
    @DisplayName("create — the provider answered")
    class CreateFromFound {

        @Test
        void takes_name_ticker_currency_type_and_source_from_the_resolved_reference() {
            Instrument instrument = instrumentFactory.create(
                    found(SecurityType.ETF, new CurrencyCode("USD"), "ISHARES CORE MSCI WORLD"),
                    null, ISIN, null, USER_ID);

            assertThat(instrument.getUserId()).isEqualTo(USER_ID);
            assertThat(instrument.getName()).isEqualTo("ISHARES CORE MSCI WORLD");
            assertThat(instrument.getIsin()).isEqualTo(ISIN);
            assertThat(instrument.getValor()).isEqualTo(VALOR);
            assertThat(instrument.getTicker()).isEqualTo("SWDA");
            assertThat(instrument.getCurrency()).isEqualTo(new CurrencyCode("USD"));
            assertThat(instrument.getInstrumentType()).isEqualTo(InstrumentType.ETF);
            assertThat(instrument.getAssetClass()).isEqualTo(AssetClass.ETF);
            assertThat(instrument.getSource()).isEqualTo(DataSource.SIX);
        }

        @Test
        void keeps_the_name_the_user_typed_over_the_one_the_provider_returns() {
            // Deliberate: the user may have a reason for their label ("Vorsorge-ETF Nina"),
            // and silently overwriting their input with vendor data is a surprise, not a
            // service. Everything else the provider says still wins.
            Instrument instrument = instrumentFactory.create(
                    found(SecurityType.ETF, new CurrencyCode("USD"), "ISHARES CORE MSCI WORLD"),
                    "Mein Welt-ETF", ISIN, null, USER_ID);

            assertThat(instrument.getName()).isEqualTo("Mein Welt-ETF");
            assertThat(instrument.getTicker()).isEqualTo("SWDA");
            assertThat(instrument.getSource()).isEqualTo(DataSource.SIX);
        }

        @Test
        void leaves_the_currency_null_when_the_provider_publishes_none() {
            // The load-bearing one. OpenFIGI is symbology and returns no currency, ever —
            // so this is the ordinary outcome for every instrument SIX does not know. CHF
            // here would make an unknown currency indistinguishable from a verified Swiss
            // one, and hand the FX converter (PR 4) a guess dressed as a fact: a USD ETF
            // would be summed into the portfolio total as francs. Exactly the bug this
            // module exists to kill.
            Instrument instrument = instrumentFactory.create(
                    found(SecurityType.ETF, null, "ISHARES CORE MSCI WORLD"),
                    null, ISIN, null, USER_ID);

            assertThat(instrument.getCurrency()).isNull();
        }

        @ParameterizedTest(name = "{0} becomes instrument type {1} and asset class {2}")
        @CsvSource({
                "EQUITY, STOCK,  STOCK",
                "ETF,    ETF,    ETF",
                "FUND,   FUND,   FUND",
                "BOND,   BOND,   BOND",
                "CRYPTO, CRYPTO, CRYPTO"
        })
        void maps_every_security_type_the_providers_can_assert(SecurityType type,
                                                               InstrumentType expectedType,
                                                               AssetClass expectedClass) {
            // A type the provider actually asserted must never be second-guessed by the
            // name heuristic — "Bitcoin Suisse AG" is a company, not a coin.
            Instrument instrument = instrumentFactory.create(
                    found(type, CurrencyCode.CHF, "Bitcoin Suisse AG"), null, ISIN, null, USER_ID);

            assertThat(instrument.getInstrumentType()).isEqualTo(expectedType);
            assertThat(instrument.getAssetClass()).isEqualTo(expectedClass);
        }

        @Test
        void falls_back_to_the_name_heuristic_when_the_provider_cannot_classify_the_security() {
            // SIX's reference feed answers OTHER for anything outside shares, ETFs and
            // public funds. Asserting STOCK there would be a worse guess than the name
            // heuristic, which at least reads "ETF" off the label.
            Instrument instrument = instrumentFactory.create(
                    found(SecurityType.OTHER, CurrencyCode.CHF, "Xtrackers MSCI World ETF"),
                    null, ISIN, null, USER_ID);

            assertThat(instrument.getAssetClass()).isEqualTo(AssetClass.ETF);
            assertThat(instrument.getInstrumentType()).isEqualTo(InstrumentType.OTHER);
        }
    }

    // =========================================================================
    // create(): nobody knew it vs. nobody could be reached
    // =========================================================================

    @Nested
    @DisplayName("create — nobody knew it (HEURISTIC), nobody answered (UNRESOLVED)")
    class CreateWithoutAProvider {

        @Test
        void classifies_an_unknown_security_by_name_and_labels_it_HEURISTIC() {
            // The unlisted CSIF share classes behind VIAC and finpension land exactly here:
            // every provider was asked and none knew them. Dropping the heuristic would
            // regress the 3a instruments this project cares most about — but the label says
            // it is a guess, and a final one: PositionService will not re-ask.
            Instrument instrument = instrumentFactory.create(
                    LookupResult.notFound(), "CSIF Switzerland Equity Fund", "CH0214967314", null, USER_ID);

            assertThat(instrument.getName()).isEqualTo("CSIF Switzerland Equity Fund");
            assertThat(instrument.getIsin()).isEqualTo("CH0214967314");
            assertThat(instrument.getAssetClass()).isEqualTo(AssetClass.FUND);
            assertThat(instrument.getInstrumentType()).isEqualTo(InstrumentType.OTHER);
            assertThat(instrument.getSource()).isEqualTo(DataSource.HEURISTIC);
        }

        @Test
        void leaves_the_currency_of_an_unknown_security_null_rather_than_guessing_CHF() {
            // Nobody said anything about this security's currency, so nothing is known
            // about it. "Unknown" is a value the schema can hold; "CHF" would be an
            // invention that later code cannot tell apart from a verified one.
            Instrument instrument = instrumentFactory.create(
                    LookupResult.notFound(), "Unbekannt AG", ISIN, null, USER_ID);

            assertThat(instrument.getCurrency()).isNull();
            assertThat(instrument.getSource()).isEqualTo(DataSource.HEURISTIC);
        }

        @Test
        void labels_an_instrument_created_during_a_provider_outage_UNRESOLVED() {
            // The blocker this whole redesign turns on. Storing this as HEURISTIC would be
            // fatal in a quiet way: the instrument exists, every later import finds it by
            // ISIN, and it is never resolved again — so a five-minute SIX outage would
            // permanently pin a name-derived guess to a real security. UNRESOLVED is a
            // to-do, and PositionService picks it up on the next touch.
            Instrument instrument = instrumentFactory.create(
                    LookupResult.unavailable("six: read timed out"),
                    "iShares Core MSCI World ETF", ISIN, VALOR, USER_ID);

            assertThat(instrument.getSource()).isEqualTo(DataSource.UNRESOLVED);
            assertThat(instrument.getCurrency()).isNull();
            assertThat(instrument.getInstrumentType()).isEqualTo(InstrumentType.OTHER);
            // The user still gets a usable instrument in the meantime — the heuristic is
            // the best available guess, it is just not recorded as a final one.
            assertThat(instrument.getAssetClass()).isEqualTo(AssetClass.ETF);
            assertThat(instrument.getName()).isEqualTo("iShares Core MSCI World ETF");
            assertThat(instrument.getIsin()).isEqualTo(ISIN);
            assertThat(instrument.getValor()).isEqualTo(VALOR);
        }

        @Test
        void an_outage_and_an_unknown_security_are_never_the_same_source() {
            // Stated as a property, because the two paths are one line apart in the factory
            // and collapsing them is the easiest possible regression.
            Instrument unreachable = instrumentFactory.create(
                    LookupResult.unavailable("six: 503"), "X", ISIN, null, USER_ID);
            Instrument unknown = instrumentFactory.create(
                    LookupResult.notFound(), "X", ISIN, null, USER_ID);

            assertThat(unreachable.getSource()).isNotEqualTo(unknown.getSource());
        }
    }

    // =========================================================================
    // enrich(): the second chance an UNRESOLVED instrument gets
    // =========================================================================

    @Nested
    @DisplayName("enrich — the retry an UNRESOLVED instrument gets")
    class Enrich {

        private Instrument unresolvedInstrument() {
            return Instrument.builder()
                    .id(java.util.UUID.randomUUID())
                    .userId(USER_ID)
                    .name("iShares Core MSCI World ETF")
                    .isin(ISIN)
                    .instrumentType(InstrumentType.OTHER)
                    .assetClass(AssetClass.ETF)
                    .currency(null)
                    .source(DataSource.UNRESOLVED)
                    .lastPrice(new java.math.BigDecimal("92.50"))
                    .sortOrder(0)
                    .build();
        }

        @Test
        void fills_in_everything_the_provider_now_knows() {
            Optional<Instrument> enriched = instrumentFactory.enrich(
                    unresolvedInstrument(),
                    found(SecurityType.ETF, new CurrencyCode("USD"), "ISHARES CORE MSCI WORLD"));

            assertThat(enriched).isPresent();
            Instrument result = enriched.orElseThrow();
            assertThat(result.getCurrency()).isEqualTo(new CurrencyCode("USD"));
            assertThat(result.getTicker()).isEqualTo("SWDA");
            assertThat(result.getValor()).isEqualTo(VALOR);
            assertThat(result.getInstrumentType()).isEqualTo(InstrumentType.ETF);
            assertThat(result.getSource()).isEqualTo(DataSource.SIX);
        }

        @Test
        void keeps_the_users_name_and_everything_else_the_provider_does_not_speak_to() {
            // toBuilder(), so the price and the id survive. An "enrichment" that wiped the
            // cached price would be a regression wearing a helpful face.
            Instrument existing = unresolvedInstrument();

            Instrument result = instrumentFactory.enrich(existing,
                    found(SecurityType.ETF, CurrencyCode.CHF, "ISHARES CORE MSCI WORLD")).orElseThrow();

            assertThat(result.getName()).isEqualTo("iShares Core MSCI World ETF");
            assertThat(result.getId()).isEqualTo(existing.getId());
            assertThat(result.getLastPrice()).isEqualByComparingTo("92.50");
        }

        @Test
        void changes_nothing_when_the_providers_are_still_unreachable() {
            // No answer, no update — and crucially the instrument keeps its UNRESOLVED
            // source, so it stays on the to-do list for the next attempt.
            assertThat(instrumentFactory.enrich(
                    unresolvedInstrument(), LookupResult.unavailable("six: still down"))).isEmpty();
        }

        @Test
        void settles_an_unresolved_instrument_as_HEURISTIC_once_the_providers_say_they_do_not_know_it() {
            // "Nobody knows this security" is a final answer, unlike "we could not ask".
            // So the instrument stops being a to-do: without this it would stay UNRESOLVED
            // and be re-queried against both vendors on every single touch, forever.
            Optional<Instrument> enriched =
                    instrumentFactory.enrich(unresolvedInstrument(), LookupResult.notFound());

            assertThat(enriched).isPresent();
            assertThat(enriched.orElseThrow().getSource()).isEqualTo(DataSource.HEURISTIC);
        }

        @Test
        void leaves_it_UNRESOLVED_while_the_providers_stay_unreachable() {
            // Nothing was learned, so nothing may be written down — it gets another chance.
            assertThat(instrumentFactory.enrich(unresolvedInstrument(), LookupResult.unavailable("six: timeout")))
                    .isEmpty();
        }
    }
}
