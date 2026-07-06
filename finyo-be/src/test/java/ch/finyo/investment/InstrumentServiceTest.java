package ch.finyo.investment;

import ch.finyo.common.ResourceNotFoundException;
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
 * Pure unit tests for InstrumentService with a mocked SixMarketDataClient.
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
    private SixMarketDataClient sixClient;

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

    private MarketDataResponse marketData(String name, BigDecimal lastPrice) {
        return new MarketDataResponse(
                "3886335", "CH0038863350", "NESN", name, "SIX", "CHF", lastPrice,
                null, null, null, null, null, null,
                null, null, null, null, "STOCK", "Consumer", null,
                OffsetDateTime.now(ZoneOffset.UTC), false);
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
    // getMarketData() — identifier resolution
    // =========================================================================

    @Test
    void getMarketData_prefers_valor_over_isin_and_ticker() {
        UUID id = UUID.randomUUID();
        Instrument instrument = instrumentBuilder()
                .id(id).valor("3886335").isin("CH0038863350").ticker("NESN").build();
        given(instrumentRepository.findByIdAndUserId(id, USER_ID)).willReturn(Optional.of(instrument));
        given(sixClient.fetchByValorOrIsin("3886335"))
                .willReturn(Optional.of(marketData("Nestlé", new BigDecimal("92.50"))));

        instrumentService.getMarketData(id, USER_ID);

        then(sixClient).should().fetchByValorOrIsin("3886335");
    }

    @Test
    void getMarketData_falls_back_to_isin_when_valor_is_missing() {
        UUID id = UUID.randomUUID();
        Instrument instrument = instrumentBuilder()
                .id(id).valor(null).isin("CH0038863350").ticker("NESN").build();
        given(instrumentRepository.findByIdAndUserId(id, USER_ID)).willReturn(Optional.of(instrument));
        given(sixClient.fetchByValorOrIsin("CH0038863350"))
                .willReturn(Optional.of(marketData("Nestlé", new BigDecimal("92.50"))));

        instrumentService.getMarketData(id, USER_ID);

        then(sixClient).should().fetchByValorOrIsin("CH0038863350");
    }

    @Test
    void getMarketData_falls_back_to_ticker_when_valor_and_isin_are_missing() {
        UUID id = UUID.randomUUID();
        Instrument instrument = instrumentBuilder()
                .id(id).valor(null).isin(null).ticker("NESN").build();
        given(instrumentRepository.findByIdAndUserId(id, USER_ID)).willReturn(Optional.of(instrument));
        given(sixClient.fetchByValorOrIsin("NESN"))
                .willReturn(Optional.of(marketData("Nestlé", new BigDecimal("92.50"))));

        instrumentService.getMarketData(id, USER_ID);

        then(sixClient).should().fetchByValorOrIsin("NESN");
    }

    // =========================================================================
    // getMarketData() — live data, cache update, fallback, error
    // =========================================================================

    @Test
    void getMarketData_returns_live_data_and_persists_the_fresh_price_and_name() {
        UUID id = UUID.randomUUID();
        Instrument instrument = instrumentBuilder().id(id).valor("3886335").name("Old Name").build();
        given(instrumentRepository.findByIdAndUserId(id, USER_ID)).willReturn(Optional.of(instrument));
        given(sixClient.fetchByValorOrIsin("3886335"))
                .willReturn(Optional.of(marketData("Nestlé SA", new BigDecimal("92.50"))));

        MarketDataResponse result = instrumentService.getMarketData(id, USER_ID);

        assertThat(result.lastPrice()).isEqualByComparingTo("92.50");
        assertThat(result.fromCache()).isFalse();
        then(instrumentRepository).should().save(argThat(i ->
                new BigDecimal("92.50").compareTo(i.getLastPrice()) == 0
                        && "Nestlé SA".equals(i.getName())
                        && i.getLastPriceUpdatedAt() != null
                        && USER_ID.equals(i.getUserId())));
    }

    @Test
    void getMarketData_keeps_the_existing_name_when_the_live_name_is_blank() {
        UUID id = UUID.randomUUID();
        Instrument instrument = instrumentBuilder().id(id).valor("3886335").name("My Custom Name").build();
        given(instrumentRepository.findByIdAndUserId(id, USER_ID)).willReturn(Optional.of(instrument));
        given(sixClient.fetchByValorOrIsin("3886335"))
                .willReturn(Optional.of(marketData(" ", new BigDecimal("92.50"))));

        instrumentService.getMarketData(id, USER_ID);

        then(instrumentRepository).should().save(argThat(i -> "My Custom Name".equals(i.getName())));
    }

    @Test
    void getMarketData_does_not_persist_anything_when_live_data_has_no_price() {
        UUID id = UUID.randomUUID();
        Instrument instrument = instrumentBuilder().id(id).valor("3886335").build();
        given(instrumentRepository.findByIdAndUserId(id, USER_ID)).willReturn(Optional.of(instrument));
        given(sixClient.fetchByValorOrIsin("3886335"))
                .willReturn(Optional.of(marketData("Nestlé", null)));

        MarketDataResponse result = instrumentService.getMarketData(id, USER_ID);

        assertThat(result.lastPrice()).isNull();
        then(instrumentRepository).should(never()).save(any());
    }

    @Test
    void getMarketData_still_returns_live_data_when_persisting_the_cached_price_fails() {
        UUID id = UUID.randomUUID();
        Instrument instrument = instrumentBuilder().id(id).valor("3886335").build();
        given(instrumentRepository.findByIdAndUserId(id, USER_ID)).willReturn(Optional.of(instrument));
        given(sixClient.fetchByValorOrIsin("3886335"))
                .willReturn(Optional.of(marketData("Nestlé", new BigDecimal("92.50"))));
        given(instrumentRepository.save(any(Instrument.class)))
                .willThrow(new IllegalStateException("db down"));

        MarketDataResponse result = instrumentService.getMarketData(id, USER_ID);

        assertThat(result.lastPrice())
                .as("cache persistence failures must never break the market-data response")
                .isEqualByComparingTo("92.50");
    }

    @Test
    void getMarketData_returns_cached_price_marked_fromCache_when_six_is_unavailable() {
        UUID id = UUID.randomUUID();
        OffsetDateTime cachedAt = OffsetDateTime.parse("2026-07-01T10:00:00Z");
        Instrument instrument = instrumentBuilder()
                .id(id)
                .valor("3886335")
                .lastPrice(new BigDecimal("90.00"))
                .lastPriceUpdatedAt(cachedAt)
                .build();
        given(instrumentRepository.findByIdAndUserId(id, USER_ID)).willReturn(Optional.of(instrument));
        given(sixClient.fetchByValorOrIsin("3886335")).willReturn(Optional.empty());

        MarketDataResponse result = instrumentService.getMarketData(id, USER_ID);

        assertThat(result.fromCache()).isTrue();
        assertThat(result.lastPrice()).isEqualByComparingTo("90.00");
        assertThat(result.dataAsOf()).isEqualTo(cachedAt);
        assertThat(result.currency()).isEqualTo("CHF");
    }

    @Test
    void getMarketData_never_calls_six_when_instrument_has_no_identifier_at_all() {
        UUID id = UUID.randomUUID();
        Instrument instrument = instrumentBuilder()
                .id(id).valor(null).isin(null).ticker(null)
                .lastPrice(new BigDecimal("50.00"))
                .build();
        given(instrumentRepository.findByIdAndUserId(id, USER_ID)).willReturn(Optional.of(instrument));

        MarketDataResponse result = instrumentService.getMarketData(id, USER_ID);

        assertThat(result.fromCache()).isTrue();
        then(sixClient).shouldHaveNoInteractions();
    }

    @Test
    void getMarketData_throws_IllegalStateException_when_neither_live_nor_cached_data_exists() {
        UUID id = UUID.randomUUID();
        Instrument instrument = instrumentBuilder().id(id).valor("999").lastPrice(null).build();
        given(instrumentRepository.findByIdAndUserId(id, USER_ID)).willReturn(Optional.of(instrument));
        given(sixClient.fetchByValorOrIsin("999")).willReturn(Optional.empty());

        assertThatThrownBy(() -> instrumentService.getMarketData(id, USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No market data available");
    }

    @Test
    void getMarketData_throws_ResourceNotFoundException_for_a_foreign_instrument() {
        UUID id = UUID.randomUUID();
        given(instrumentRepository.findByIdAndUserId(id, "attacker")).willReturn(Optional.empty());

        assertThatThrownBy(() -> instrumentService.getMarketData(id, "attacker"))
                .isInstanceOf(ResourceNotFoundException.class);
        then(sixClient).shouldHaveNoInteractions();
    }
}
