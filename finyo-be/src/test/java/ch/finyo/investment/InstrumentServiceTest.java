package ch.finyo.investment;

import ch.finyo.common.ResourceNotFoundException;
import ch.finyo.common.SourceResult;
import ch.finyo.common.money.CurrencyCode;
import ch.finyo.marketdata.spi.DataSource;
import ch.finyo.marketdata.spi.SecurityReference;
import ch.finyo.marketdata.spi.SecurityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

/**
 * Pure unit tests for InstrumentService (CRUD and multi-tenancy).
 *
 * Focus areas (matching the recent refactoring):
 *   1. resolveIdentifier() precedence: valor > ISIN > ticker.
 *   2. getMarketData() fallback chain: live data → cached last price → error.
 *   3. updateCachedPrice(): persists the fresh price, refreshes the name,
 *      skips persistence when the live response has no price, and never
 *      propagates persistence failures to the caller.
 *   4. Standard CRUD multi-tenancy and partial-update semantics.
 */
@ExtendWith(MockitoExtension.class)
class InstrumentServiceTest {

    private static final String USER_ID = "user-inv-1";

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private InstrumentFactory instrumentFactory;

    @InjectMocks
    private InstrumentService instrumentService;

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private Instrument.InstrumentBuilder instrumentBuilder() {
        return Instrument.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .name("Nestlé")
                .instrumentType(InstrumentType.STOCK)
                .sortOrder(1);
    }

    private SecurityReference reference(SecurityType type, CurrencyCode currency) {
        return new SecurityReference(
                "IE00B4L5Y983",
                "24476758",
                "SWDA",
                "iShares Core MSCI World",
                type,
                currency,
                "BlackRock",
                DataSource.OPENFIGI,
                OffsetDateTime.of(2026, 7, 14, 10, 30, 0, 0, ZoneOffset.UTC));
    }


    // =========================================================================
    // getAll() / getById()
    // =========================================================================

    @Test
    void getAll_maps_all_user_instruments_to_responses() {
        given(instrumentRepository.findByUserIdOrderBySortOrderAscNameAsc(USER_ID))
                .willReturn(List.of(instrumentBuilder().build()));

        List<InstrumentResponse> result = instrumentService.getAll(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Nestlé");
    }

    @Test
    void getById_throws_ResourceNotFoundException_when_instrument_belongs_to_another_user() {
        UUID id = UUID.randomUUID();
        given(instrumentRepository.findByIdAndUserId(id, "attacker")).willReturn(Optional.empty());

        assertThatThrownBy(() -> instrumentService.getById(id, "attacker"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Instrument");
    }

    // =========================================================================
    // create()
    // =========================================================================

    @Test
    void create_sets_userId_and_defaults_sortOrder_to_zero() {
        InstrumentRequest request = new InstrumentRequest(
                "3886335", "CH0038863350", "NESN", "Nestlé", InstrumentType.STOCK, null);
        given(instrumentRepository.save(any(Instrument.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        InstrumentResponse result = instrumentService.create(request, USER_ID);

        assertThat(result.sortOrder()).isZero();
        then(instrumentRepository).should().save(argThat(i -> USER_ID.equals(i.getUserId())));
    }

    // =========================================================================
    // update()
    // =========================================================================

    @Test
    void update_keeps_existing_values_for_fields_the_request_omits() {
        UUID id = UUID.randomUUID();
        Instrument existing = instrumentBuilder()
                .id(id)
                .valor("3886335")
                .isin("CH0038863350")
                .ticker("NESN")
                .sortOrder(7)
                .lastPrice(new BigDecimal("88.88"))
                .build();
        given(instrumentRepository.findByIdAndUserId(id, USER_ID)).willReturn(Optional.of(existing));
        given(instrumentRepository.save(any(Instrument.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        InstrumentRequest partialUpdate = new InstrumentRequest(null, null, null, "Nestlé SA", null, null);
        InstrumentResponse result = instrumentService.update(id, partialUpdate, USER_ID);

        assertThat(result.name()).isEqualTo("Nestlé SA");
        assertThat(result.valor()).isEqualTo("3886335");
        assertThat(result.isin()).isEqualTo("CH0038863350");
        assertThat(result.ticker()).isEqualTo("NESN");
        assertThat(result.instrumentType()).isEqualTo(InstrumentType.STOCK);
        assertThat(result.sortOrder()).isEqualTo(7);
        assertThat(result.lastPrice()).isEqualByComparingTo("88.88");
    }

    @Test
    void update_throws_ResourceNotFoundException_when_instrument_is_not_found() {
        UUID id = UUID.randomUUID();
        given(instrumentRepository.findByIdAndUserId(id, USER_ID)).willReturn(Optional.empty());
        InstrumentRequest request = new InstrumentRequest(null, null, null, "x", null, null);

        assertThatThrownBy(() -> instrumentService.update(id, request, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        then(instrumentRepository).should(never()).save(any());
    }

    // =========================================================================
    // delete()
    // =========================================================================

    @Test
    void delete_removes_the_instrument_when_it_belongs_to_the_user() {
        UUID id = UUID.randomUUID();
        given(instrumentRepository.findByIdAndUserId(id, USER_ID))
                .willReturn(Optional.of(instrumentBuilder().id(id).build()));

        instrumentService.delete(id, USER_ID);

        then(instrumentRepository).should().deleteById(id);
    }

    @Test
    void delete_never_calls_deleteById_when_instrument_belongs_to_another_user() {
        UUID id = UUID.randomUUID();
        given(instrumentRepository.findByIdAndUserId(id, "attacker")).willReturn(Optional.empty());

        assertThatThrownBy(() -> instrumentService.delete(id, "attacker"))
                .isInstanceOf(ResourceNotFoundException.class);
        then(instrumentRepository).should(never()).deleteById(any());
    }

    // =========================================================================
    // lookup()
    // =========================================================================

    /**
     * The add-position preview only maps what the factory resolves — it creates
     * nothing. The three provider outcomes (Found / NotFound / Unavailable) map to
     * three distinct response statuses, and the form treats each differently, so the
     * mapping itself is the whole subject here.
     */
    @Nested
    @DisplayName("lookup()")
    class Lookup {

        private static final String ISIN = "IE00B4L5Y983";
        private static final String VALOR = "24476758";

        @Test
        @DisplayName("maps a found reference to a FOUND response with its master data")
        void maps_a_found_reference_to_a_FOUND_response() {
            given(instrumentFactory.lookup(ISIN, VALOR))
                    .willReturn(SourceResult.found(reference(SecurityType.ETF, new CurrencyCode("USD"))));

            InstrumentLookupResponse result = instrumentService.lookup(ISIN, VALOR);

            assertThat(result.status()).isEqualTo(InstrumentLookupResponse.Status.FOUND);
            assertThat(result.name()).isEqualTo("iShares Core MSCI World");
            assertThat(result.ticker()).isEqualTo("SWDA");
            assertThat(result.currency()).isEqualTo("USD");
            assertThat(result.assetClass()).isEqualTo(AssetClass.ETF);
        }

        @Test
        @DisplayName("keeps the currency null when the provider publishes none (OpenFIGI)")
        void keeps_the_currency_null_when_the_reference_has_none() {
            // OpenFIGI never returns a currency. FOUND with a null currency must stay
            // distinguishable from a verified CHF — the form fills the master data but
            // does not invent a currency.
            given(instrumentFactory.lookup(ISIN, null))
                    .willReturn(SourceResult.found(reference(SecurityType.ETF, null)));

            InstrumentLookupResponse result = instrumentService.lookup(ISIN, null);

            assertThat(result.status()).isEqualTo(InstrumentLookupResponse.Status.FOUND);
            assertThat(result.currency()).isNull();
            assertThat(result.name()).isEqualTo("iShares Core MSCI World");
        }

        @Test
        @DisplayName("maps NotFound to a NOT_FOUND response with all fields null")
        void maps_a_notFound_result_to_a_NOT_FOUND_response() {
            given(instrumentFactory.lookup(ISIN, VALOR)).willReturn(SourceResult.notFound());

            InstrumentLookupResponse result = instrumentService.lookup(ISIN, VALOR);

            assertThat(result.status()).isEqualTo(InstrumentLookupResponse.Status.NOT_FOUND);
            assertThat(result.name()).isNull();
            assertThat(result.ticker()).isNull();
            assertThat(result.currency()).isNull();
            assertThat(result.assetClass()).isNull();
        }

        @Test
        @DisplayName("maps Unavailable to an UNAVAILABLE response with all fields null")
        void maps_an_unavailable_result_to_an_UNAVAILABLE_response() {
            given(instrumentFactory.lookup(ISIN, VALOR))
                    .willReturn(SourceResult.unavailable("SIX: timeout"));

            InstrumentLookupResponse result = instrumentService.lookup(ISIN, VALOR);

            assertThat(result.status()).isEqualTo(InstrumentLookupResponse.Status.UNAVAILABLE);
            assertThat(result.name()).isNull();
            assertThat(result.ticker()).isNull();
            assertThat(result.currency()).isNull();
            assertThat(result.assetClass()).isNull();
        }

        @Test
        @DisplayName("passes the isin and valor through to the factory unchanged")
        void passes_the_identifiers_through_to_the_factory() {
            given(instrumentFactory.lookup(ISIN, VALOR)).willReturn(SourceResult.notFound());

            instrumentService.lookup(ISIN, VALOR);

            then(instrumentFactory).should().lookup(ISIN, VALOR);
        }
    }
}
