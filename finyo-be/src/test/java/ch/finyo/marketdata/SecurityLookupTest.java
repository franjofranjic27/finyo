package ch.finyo.marketdata;

import ch.finyo.common.money.CurrencyCode;
import ch.finyo.marketdata.spi.DataSource;
import ch.finyo.common.SourceResult;
import ch.finyo.marketdata.spi.SecurityId;
import ch.finyo.marketdata.spi.SecurityReference;
import ch.finyo.marketdata.spi.SecurityReferenceProvider;
import ch.finyo.marketdata.spi.SecurityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static ch.finyo.common.SourceResults.foundValue;
import static ch.finyo.common.SourceResults.unavailableReason;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.times;

/**
 * Unit tests for SecurityLookup — the provider chain.
 *
 * This class is where finyo's availability property lives, so the tests are written
 * against that property rather than against the code: the lookup must keep answering
 * when SIX is down, when a vendor changes its wire format under us, and on the day
 * SIX has to be switched off for licensing reasons.
 *
 * The single most important group below is "unreachable is not unknown". Both used to
 * be {@code Optional.empty()}, and the consequence was not academic: during a SIX
 * outage every imported instrument was written down as a name-derived guess and then
 * found by ISIN on every later import, so it was never resolved again. The guess became
 * permanent. {@link SourceResult} exists to make that impossible, and these tests are
 * what keep it impossible.
 *
 * The providers are mocks on purpose — the chain's behaviour must not depend on which
 * vendors happen to exist, which is exactly what makes swapping one a YAML change.
 */
@DisplayName("SecurityLookup")
@ExtendWith(MockitoExtension.class)
class SecurityLookupTest {

    private static final String ISIN = "IE00B4L5Y983";
    private static final String VALOR = "24476758";
    private static final String TICKER = "SWDA";

    private static final OffsetDateTime RETRIEVED_AT =
            OffsetDateTime.of(2026, 7, 14, 10, 30, 0, 0, ZoneOffset.UTC);

    @Mock
    private SecurityReferenceProvider six;

    @Mock
    private SecurityReferenceProvider openfigi;

    @Mock
    private SecurityReferenceRepository repository;

    @Mock
    private SecurityReferenceCache cache;

    @BeforeEach
    void nameTheProviders() {
        // The chain is assembled by name, so every provider must answer name() before
        // it can be wired — including the ones a test then expects to be filtered out.
        given(six.name()).willReturn("six");
        given(openfigi.name()).willReturn("openfigi");
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    /**
     * The bean order is deliberately [six, openfigi] everywhere so that a test which
     * configures the reverse order proves configuration wins, not luck.
     */
    private SecurityLookup lookupWithChain(String... configuredOrder) {
        MarketDataProperties properties = new MarketDataProperties(
                List.of(configuredOrder),
                // Quote providers are a separate chain and none of SecurityLookup's business.
                List.of(),
                // History providers are a separate chain too.
                List.of(),
                new MarketDataProperties.SixProperties(true, "http://six.invalid"),
                new MarketDataProperties.OpenFigiProperties(true, "http://openfigi.invalid", null),
                new MarketDataProperties.EodhdProperties(false, null, null));

        return new SecurityLookup(List.of(six, openfigi), properties, repository, cache);
    }

    private static SecurityReference reference(DataSource source) {
        return new SecurityReference(ISIN, VALOR, TICKER, "ISHARES CORE MSCI WORLD",
                SecurityType.ETF, new CurrencyCode("USD"), "BlackRock", source, RETRIEVED_AT);
    }

    private static CachedSecurityReference cachedReference() {
        return CachedSecurityReference.builder()
                .isin(ISIN)
                .valor(VALOR)
                .ticker(TICKER)
                .name("ISHARES CORE MSCI WORLD")
                .type(SecurityType.ETF)
                .currency(new CurrencyCode("USD"))
                .issuer("BlackRock")
                .source(DataSource.SIX)
                .retrievedAt(RETRIEVED_AT)
                .build();
    }

    private void givenProviderSupportsEverything(SecurityReferenceProvider provider) {
        given(provider.supports(any())).willReturn(true);
    }

    private void givenNothingIsCached() {
        given(repository.findById(any())).willReturn(Optional.empty());
    }

    // =========================================================================
    // Cache first
    // =========================================================================

    @Nested
    @DisplayName("the persistent cache answers before any provider does")
    class CacheFirst {

        @Test
        void a_cache_hit_never_asks_a_provider() {
            // The offline guarantee: reference data barely changes, and a stale name is
            // worth infinitely more than a failed request.
            given(repository.findById(ISIN)).willReturn(Optional.of(cachedReference()));
            SecurityLookup lookup = lookupWithChain("six", "openfigi");

            SourceResult<SecurityReference> result = lookup.resolve(new SecurityId.Isin(ISIN));

            assertThat(foundValue(result).isin()).isEqualTo(ISIN);
            then(six).should(never()).lookup(any());
            then(openfigi).should(never()).lookup(any());
            then(cache).shouldHaveNoInteractions();
        }

        @Test
        void a_lookup_by_valor_reads_the_cache_by_valor() {
            // A valor is what a Swiss bank statement shows — it has to hit the valor
            // index, not scan for an ISIN it does not have.
            given(repository.findFirstByValor(VALOR)).willReturn(Optional.of(cachedReference()));
            SecurityLookup lookup = lookupWithChain("six");

            SourceResult<SecurityReference> result = lookup.resolve(new SecurityId.Valor(VALOR));

            assertThat(foundValue(result).valor()).isEqualTo(VALOR);
            then(repository).should().findFirstByValor(VALOR);
            then(six).should(never()).lookup(any());
        }

        @Test
        void answers_from_the_cache_alone_when_the_chain_is_empty() {
            // The state the test profile runs in, and the state production lands in if
            // every provider is switched off: the persistent cache is still a source.
            given(repository.findById(ISIN)).willReturn(Optional.of(cachedReference()));
            SecurityLookup lookup = lookupWithChain();

            assertThat(lookup.resolve(new SecurityId.Isin(ISIN))).isInstanceOf(SourceResult.Found.class);
        }
    }

    // =========================================================================
    // Cache miss: the chain
    // =========================================================================

    @Nested
    @DisplayName("on a cache miss the chain is asked, in configuration order")
    class TheChain {

        @Test
        void a_cache_miss_asks_the_first_provider_and_hands_the_answer_to_the_cache() {
            givenNothingIsCached();
            givenProviderSupportsEverything(six);
            given(six.lookup(any())).willReturn(SourceResult.found(reference(DataSource.SIX)));
            SecurityLookup lookup = lookupWithChain("six", "openfigi");

            SourceResult<SecurityReference> result = lookup.resolve(new SecurityId.Isin(ISIN));

            assertThat(foundValue(result)).isEqualTo(reference(DataSource.SIX));
            then(openfigi).should(never()).lookup(any());
            then(cache).should().store(reference(DataSource.SIX));
        }

        @Test
        void falls_through_to_the_next_provider_when_the_first_one_does_not_know_the_security() {
            // The reason OpenFIGI is in the chain at all: SIX answers totalRows: 0 for the
            // unlisted CSIF funds behind VIAC and finpension, OpenFIGI resolves them.
            givenNothingIsCached();
            givenProviderSupportsEverything(six);
            givenProviderSupportsEverything(openfigi);
            given(six.lookup(any())).willReturn(SourceResult.notFound());
            given(openfigi.lookup(any())).willReturn(SourceResult.found(reference(DataSource.OPENFIGI)));
            SecurityLookup lookup = lookupWithChain("six", "openfigi");

            SourceResult<SecurityReference> result = lookup.resolve(new SecurityId.Isin(ISIN));

            assertThat(foundValue(result).source()).isEqualTo(DataSource.OPENFIGI);
            then(six).should().lookup(any());
        }

        @Test
        void falls_through_to_the_next_provider_when_the_first_one_is_unavailable() {
            // A SIX outage must not cost the answer when OpenFIGI is up.
            givenNothingIsCached();
            givenProviderSupportsEverything(six);
            givenProviderSupportsEverything(openfigi);
            given(six.lookup(any())).willReturn(SourceResult.unavailable("six: read timed out"));
            given(openfigi.lookup(any())).willReturn(SourceResult.found(reference(DataSource.OPENFIGI)));
            SecurityLookup lookup = lookupWithChain("six", "openfigi");

            SourceResult<SecurityReference> result = lookup.resolve(new SecurityId.Isin(ISIN));

            assertThat(foundValue(result).source()).isEqualTo(DataSource.OPENFIGI);
        }

        @Test
        void a_provider_that_blows_up_does_not_take_the_chain_down_with_it() {
            // The adapters already turn expected failures into Unavailable. This covers the
            // unexpected one — a vendor silently changing its wire format — which must
            // degrade to "the next provider answers", not to a 500 for the user.
            givenNothingIsCached();
            givenProviderSupportsEverything(six);
            givenProviderSupportsEverything(openfigi);
            given(six.lookup(any())).willThrow(new IllegalStateException("FQS changed its payload"));
            given(openfigi.lookup(any())).willReturn(SourceResult.found(reference(DataSource.OPENFIGI)));
            SecurityLookup lookup = lookupWithChain("six", "openfigi");

            SourceResult<SecurityReference> result = lookup.resolve(new SecurityId.Isin(ISIN));

            assertThat(foundValue(result).source()).isEqualTo(DataSource.OPENFIGI);
        }

        @Test
        void skips_a_provider_that_cannot_resolve_this_kind_of_identifier() {
            // OpenFIGI has no concept of a Swiss valor number. Asking it anyway would burn
            // a rate-limited request on a guaranteed miss.
            given(repository.findFirstByValor(VALOR)).willReturn(Optional.empty());
            given(openfigi.supports(any())).willReturn(false);
            givenProviderSupportsEverything(six);
            given(six.lookup(any())).willReturn(SourceResult.found(reference(DataSource.SIX)));
            SecurityLookup lookup = lookupWithChain("openfigi", "six");

            SourceResult<SecurityReference> result = lookup.resolve(new SecurityId.Valor(VALOR));

            assertThat(foundValue(result).source()).isEqualTo(DataSource.SIX);
            then(openfigi).should(never()).lookup(any());
        }

        @Test
        void hands_on_a_reference_that_has_no_isin_even_though_it_cannot_be_the_cache_key() {
            // The ISIN is the cache table's primary key, but its absence is a caching
            // problem, not the caller's. The answer still gets through; whether it can be
            // stored is SecurityReferenceCache's business.
            givenNothingIsCached();
            givenProviderSupportsEverything(six);
            SecurityReference withoutIsin = new SecurityReference(
                    null, null, TICKER, "ISHARES CORE MSCI WORLD", SecurityType.ETF,
                    CurrencyCode.CHF, null, DataSource.SIX, RETRIEVED_AT);
            given(six.lookup(any())).willReturn(SourceResult.found(withoutIsin));
            SecurityLookup lookup = lookupWithChain("six");

            SourceResult<SecurityReference> result = lookup.resolve(new SecurityId.Isin(ISIN));

            assertThat(foundValue(result)).isEqualTo(withoutIsin);
        }
    }

    // =========================================================================
    // "Unreachable" is not "unknown" — the distinction the whole type exists for
    // =========================================================================

    @Nested
    @DisplayName("an unreachable provider is not the same as an unknown security")
    class UnreachableIsNotUnknown {

        @Test
        void reports_Unavailable_when_the_only_provider_could_not_be_reached() {
            // NOT NotFound. NotFound licenses the caller to write a name-derived guess
            // down as fact and never ask again — during an outage that is how a guess
            // becomes permanent.
            givenNothingIsCached();
            givenProviderSupportsEverything(six);
            given(six.lookup(any())).willReturn(SourceResult.unavailable("six: read timed out"));
            SecurityLookup lookup = lookupWithChain("six");

            SourceResult<SecurityReference> result = lookup.resolve(new SecurityId.Isin(ISIN));

            assertThat(unavailableReason(result)).contains("read timed out");
            then(cache).should(never()).store(any());
        }

        @Test
        void reports_Unavailable_when_one_provider_says_NotFound_and_another_is_unreachable() {
            // The load-bearing case. OpenFIGI honestly does not know the security, SIX
            // never answered — so the chain has established nothing at all. Calling that
            // NotFound would be a guess dressed as a finding.
            givenNothingIsCached();
            givenProviderSupportsEverything(six);
            givenProviderSupportsEverything(openfigi);
            given(six.lookup(any())).willReturn(SourceResult.unavailable("six: connection refused"));
            given(openfigi.lookup(any())).willReturn(SourceResult.notFound());
            SecurityLookup lookup = lookupWithChain("six", "openfigi");

            SourceResult<SecurityReference> result = lookup.resolve(new SecurityId.Isin(ISIN));

            assertThat(unavailableReason(result)).contains("connection refused");
        }

        @Test
        void reports_Unavailable_even_when_the_unreachable_provider_was_asked_last() {
            // Order must not decide the verdict: whoever was unreachable, the chain still
            // knows nothing about the security.
            givenNothingIsCached();
            givenProviderSupportsEverything(six);
            givenProviderSupportsEverything(openfigi);
            given(six.lookup(any())).willReturn(SourceResult.notFound());
            given(openfigi.lookup(any())).willReturn(SourceResult.unavailable("openfigi: 429"));
            SecurityLookup lookup = lookupWithChain("six", "openfigi");

            SourceResult<SecurityReference> result = lookup.resolve(new SecurityId.Isin(ISIN));

            assertThat(unavailableReason(result)).contains("429");
        }

        @Test
        void reports_NotFound_only_when_every_provider_answered_and_none_knew_the_security() {
            // A legitimate outcome, not a failure: no free source resolves the unlisted 3a
            // share classes. This is the one case in which InstrumentFactory may fall back
            // to the name heuristic and call the result final.
            givenNothingIsCached();
            givenProviderSupportsEverything(six);
            givenProviderSupportsEverything(openfigi);
            given(six.lookup(any())).willReturn(SourceResult.notFound());
            given(openfigi.lookup(any())).willReturn(SourceResult.notFound());
            SecurityLookup lookup = lookupWithChain("six", "openfigi");

            SourceResult<SecurityReference> result = lookup.resolve(new SecurityId.Isin(ISIN));

            assertThat(result).isEqualTo(SourceResult.notFound());
            then(cache).should(never()).store(any());
        }

        @Test
        void reports_Unavailable_when_a_provider_throws_an_unexpected_exception() {
            // An exploding adapter says nothing about the security either — it is a vendor
            // problem, and must be reported as one rather than swallowed into NotFound.
            givenNothingIsCached();
            givenProviderSupportsEverything(six);
            given(six.lookup(any())).willThrow(new IllegalStateException("FQS changed its payload"));
            SecurityLookup lookup = lookupWithChain("six");

            SourceResult<SecurityReference> result = lookup.resolve(new SecurityId.Isin(ISIN));

            assertThat(unavailableReason(result)).contains("six").contains("IllegalStateException");
        }

        @Test
        void reports_NotFound_when_the_chain_is_empty_and_the_cache_does_not_have_it() {
            // Nobody was asked, but nobody was unreachable either. The distinction only
            // matters where a provider actually failed.
            given(repository.findById(any())).willReturn(Optional.empty());
            SecurityLookup lookup = lookupWithChain();

            assertThat(lookup.resolve(new SecurityId.Isin(ISIN))).isEqualTo(SourceResult.notFound());
        }
    }

    // =========================================================================
    // The in-memory negative cache
    // =========================================================================

    @Nested
    @DisplayName("misses are remembered, outages are not")
    class NegativeCache {

        @Test
        void a_second_lookup_of_an_unknown_security_does_not_ask_the_providers_again() {
            // The unlisted 3a funds are the common case, and each one costs a round trip to
            // every provider before coming back empty. Re-importing the same 3a portfolio
            // would otherwise hammer two vendors we are merely tolerated on, every time.
            givenNothingIsCached();
            givenProviderSupportsEverything(six);
            given(six.lookup(any())).willReturn(SourceResult.notFound());
            SecurityLookup lookup = lookupWithChain("six");

            lookup.resolve(new SecurityId.Isin(ISIN));
            SourceResult<SecurityReference> second = lookup.resolve(new SecurityId.Isin(ISIN));

            assertThat(second).isEqualTo(SourceResult.notFound());
            then(six).should(times(1)).lookup(any());
        }

        @Test
        void an_unavailable_provider_is_never_remembered_as_a_miss() {
            // The trap this test exists for: caching an outage would freeze it. A SIX
            // timeout would turn into "this security does not exist" for the next hour,
            // and every instrument imported in that hour would be written down as a guess
            // — which is the exact failure SourceResult was introduced to prevent, moved
            // one layer inwards.
            givenNothingIsCached();
            givenProviderSupportsEverything(six);
            given(six.lookup(any()))
                    .willReturn(SourceResult.unavailable("six: read timed out"))
                    .willReturn(SourceResult.found(reference(DataSource.SIX)));
            SecurityLookup lookup = lookupWithChain("six");

            SourceResult<SecurityReference> first = lookup.resolve(new SecurityId.Isin(ISIN));
            SourceResult<SecurityReference> second = lookup.resolve(new SecurityId.Isin(ISIN));

            assertThat(unavailableReason(first)).contains("read timed out");
            assertThat(foundValue(second).source()).isEqualTo(DataSource.SIX);
            then(six).should(times(2)).lookup(any());
        }

        @Test
        void remembering_a_miss_for_one_security_does_not_answer_for_another() {
            givenNothingIsCached();
            givenProviderSupportsEverything(six);
            given(six.lookup(any()))
                    .willReturn(SourceResult.notFound())
                    .willReturn(SourceResult.found(reference(DataSource.SIX)));
            SecurityLookup lookup = lookupWithChain("six");

            lookup.resolve(new SecurityId.Isin("CH0214967314"));
            SourceResult<SecurityReference> other = lookup.resolve(new SecurityId.Isin(ISIN));

            assertThat(foundValue(other).source()).isEqualTo(DataSource.SIX);
        }
    }

    // =========================================================================
    // Configuration owns the chain
    // =========================================================================

    @Nested
    @DisplayName("configuration decides who runs, and in which order")
    class Configuration {

        @Test
        void ignores_a_provider_that_is_not_in_the_configured_chain_even_though_its_bean_exists() {
            // This is what makes "switch SIX off" a three-line YAML change on the day the
            // personal-use licence stops holding. If a bean could answer without being
            // listed, "enabled: false" and "not in the chain" could disagree — and the one
            // that wins would be the one nobody checked.
            givenNothingIsCached();
            givenProviderSupportsEverything(openfigi);
            given(openfigi.lookup(any())).willReturn(SourceResult.found(reference(DataSource.OPENFIGI)));
            SecurityLookup lookup = lookupWithChain("openfigi");

            SourceResult<SecurityReference> result = lookup.resolve(new SecurityId.Isin(ISIN));

            assertThat(foundValue(result).source()).isEqualTo(DataSource.OPENFIGI);
            then(six).should(never()).supports(any());
            then(six).should(never()).lookup(any());
        }

        @Test
        void asks_the_providers_in_the_configured_order_not_in_bean_order() {
            // The beans are handed in as [six, openfigi]; the configuration says the
            // opposite. The configuration wins — otherwise the fallback order would be an
            // accident of classpath scanning.
            givenNothingIsCached();
            givenProviderSupportsEverything(openfigi);
            given(openfigi.lookup(any())).willReturn(SourceResult.found(reference(DataSource.OPENFIGI)));
            SecurityLookup lookup = lookupWithChain("openfigi", "six");

            SourceResult<SecurityReference> result = lookup.resolve(new SecurityId.Isin(ISIN));

            assertThat(foundValue(result).source()).isEqualTo(DataSource.OPENFIGI);
            then(six).should(never()).lookup(any());
        }
    }
}
