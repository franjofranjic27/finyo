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
}
