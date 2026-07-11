package ch.finyo.investment;

import ch.finyo.common.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

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
import static org.mockito.BDDMockito.times;

/**
 * Pure unit tests for PositionService.
 *
 * Focus areas:
 *   1. Instrument resolution: reuse by ISIN (before valor), auto-creation
 *      including the SIX name refresh for unknown identifiers.
 *   2. currentPrice override: applied only when no market price exists
 *      after the SIX refresh.
 *   3. Merge semantics: weighted average purchase price at scale 4 HALF_UP.
 *   4. Bulk import: row errors are collected without aborting the import.
 *   5. Multi-tenancy on delete.
 */
@ExtendWith(MockitoExtension.class)
class PositionServiceTest {

    private static final String USER_ID = "user-pos-1";
    private static final UUID INSTRUMENT_ID = UUID.randomUUID();
    private static final String ISIN = "CH0038863350";
    private static final String VALOR = "3886335";

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private InstrumentService instrumentService;

    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private PositionService positionService;

    // -------------------------------------------------------------------------
    // Builders & stubbing helpers
    // -------------------------------------------------------------------------

    private Instrument.InstrumentBuilder instrumentBuilder() {
        return Instrument.builder()
                .id(INSTRUMENT_ID)
                .userId(USER_ID)
                .name("Nestlé")
                .isin(ISIN)
                .valor(VALOR)
                .instrumentType(InstrumentType.STOCK)
                .sortOrder(0);
    }

    private PositionRequest request(String name, String isin, String valor,
                                    String quantity, String purchasePrice, String currentPrice) {
        return new PositionRequest(name, isin, valor,
                quantity != null ? new BigDecimal(quantity) : null,
                purchasePrice != null ? new BigDecimal(purchasePrice) : null,
                currentPrice != null ? new BigDecimal(currentPrice) : null);
    }

    /** save() assigns INSTRUMENT_ID on insert and echoes updates unchanged. */
    private void stubInstrumentSaveEchoesArgument() {
        given(instrumentRepository.save(any(Instrument.class))).willAnswer(invocation -> {
            Instrument i = invocation.getArgument(0);
            if (i.getId() != null) {
                return i;
            }
            return Instrument.builder()
                    .id(INSTRUMENT_ID)
                    .userId(i.getUserId())
                    .name(i.getName())
                    .isin(i.getIsin())
                    .valor(i.getValor())
                    .ticker(i.getTicker())
                    .instrumentType(i.getInstrumentType())
                    .sortOrder(i.getSortOrder())
                    .lastPrice(i.getLastPrice())
                    .lastPriceUpdatedAt(i.getLastPriceUpdatedAt())
                    .build();
        });
    }

    private void stubPositionSaveEchoesArgument() {
        given(positionRepository.save(any(Position.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubSixRefreshReturnsInputUnchanged() {
        given(instrumentService.refreshPriceFromSix(any(Instrument.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    // =========================================================================
    // create() — instrument resolution
    // =========================================================================

    @Test
    void create_reuses_the_existing_instrument_found_by_isin() {
        Instrument existing = instrumentBuilder().lastPrice(new BigDecimal("92.50")).build();
        given(instrumentRepository.findFirstByUserIdAndIsinIgnoreCase(USER_ID, ISIN))
                .willReturn(Optional.of(existing));
        stubSixRefreshReturnsInputUnchanged();
        given(positionRepository.findByUserIdAndInstrumentId(USER_ID, INSTRUMENT_ID))
                .willReturn(Optional.empty());
        stubPositionSaveEchoesArgument();

        PositionResponse result = positionService.create(
                request(null, ISIN, VALOR, "10", "80.00", null), USER_ID);

        assertThat(result.instrumentId()).isEqualTo(INSTRUMENT_ID);
        assertThat(result.name()).isEqualTo("Nestlé");
        then(instrumentRepository).should(never()).save(any());
        then(instrumentRepository).should(never()).findFirstByUserIdAndValor(any(), any());
    }

    @Test
    void create_falls_back_to_valor_lookup_when_isin_is_unknown() {
        Instrument existing = instrumentBuilder().isin(null).lastPrice(new BigDecimal("92.50")).build();
        given(instrumentRepository.findFirstByUserIdAndIsinIgnoreCase(USER_ID, ISIN))
                .willReturn(Optional.empty());
        given(instrumentRepository.findFirstByUserIdAndValor(USER_ID, VALOR))
                .willReturn(Optional.of(existing));
        stubSixRefreshReturnsInputUnchanged();
        given(positionRepository.findByUserIdAndInstrumentId(USER_ID, INSTRUMENT_ID))
                .willReturn(Optional.empty());
        stubPositionSaveEchoesArgument();

        PositionResponse result = positionService.create(
                request(null, ISIN, VALOR, "10", "80.00", null), USER_ID);

        assertThat(result.instrumentId()).isEqualTo(INSTRUMENT_ID);
        then(instrumentRepository).should(never()).save(any());
    }

    @Test
    void create_auto_creates_the_instrument_and_takes_the_name_from_six() {
        given(instrumentRepository.findFirstByUserIdAndIsinIgnoreCase(USER_ID, ISIN))
                .willReturn(Optional.empty());
        stubInstrumentSaveEchoesArgument();
        given(instrumentService.refreshPriceFromSix(any(Instrument.class)))
                .willAnswer(invocation -> {
                    Instrument i = invocation.getArgument(0);
                    return Instrument.builder()
                            .id(i.getId()).userId(i.getUserId())
                            .isin(i.getIsin()).valor(i.getValor())
                            .name("Nestlé SA")
                            .instrumentType(i.getInstrumentType()).sortOrder(i.getSortOrder())
                            .lastPrice(new BigDecimal("92.50"))
                            .lastPriceUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                            .build();
                });
        given(positionRepository.findByUserIdAndInstrumentId(USER_ID, INSTRUMENT_ID))
                .willReturn(Optional.empty());
        stubPositionSaveEchoesArgument();

        PositionResponse result = positionService.create(
                request(null, ISIN, null, "10", "80.00", null), USER_ID);

        assertThat(result.name()).isEqualTo("Nestlé SA");
        then(instrumentRepository).should().save(argThat(i ->
                i.getId() == null && ISIN.equals(i.getIsin()) && USER_ID.equals(i.getUserId())));
        then(instrumentService).should().refreshPriceFromSix(argThat(i -> INSTRUMENT_ID.equals(i.getId())));
    }

    // =========================================================================
    // create() — currentPrice override
    // =========================================================================

    @Test
    void create_applies_the_currentPrice_override_when_no_market_price_exists() {
        stubInstrumentSaveEchoesArgument();
        stubSixRefreshReturnsInputUnchanged();
        given(positionRepository.findByUserIdAndInstrumentId(USER_ID, INSTRUMENT_ID))
                .willReturn(Optional.empty());
        stubPositionSaveEchoesArgument();

        positionService.create(request("Manual Fund", null, null, "10", "100.00", "110.00"), USER_ID);

        // one save for auto-creation, one for the price override
        then(instrumentRepository).should(times(2)).save(any(Instrument.class));
        then(instrumentRepository).should().save(argThat(i ->
                i.getId() != null
                        && i.getLastPrice() != null
                        && new BigDecimal("110.00").compareTo(i.getLastPrice()) == 0
                        && i.getLastPriceUpdatedAt() != null));
    }

    @Test
    void create_ignores_the_currentPrice_override_when_a_market_price_already_exists() {
        Instrument existing = instrumentBuilder().lastPrice(new BigDecimal("92.50")).build();
        given(instrumentRepository.findFirstByUserIdAndIsinIgnoreCase(USER_ID, ISIN))
                .willReturn(Optional.of(existing));
        stubSixRefreshReturnsInputUnchanged();
        given(positionRepository.findByUserIdAndInstrumentId(USER_ID, INSTRUMENT_ID))
                .willReturn(Optional.empty());
        stubPositionSaveEchoesArgument();

        positionService.create(request(null, ISIN, null, "10", "80.00", "50.00"), USER_ID);

        then(instrumentRepository).should(never()).save(any());
    }

    // =========================================================================
    // create() — merge semantics
    // =========================================================================

    @Test
    void create_merges_into_an_existing_position_with_a_weighted_average_price() {
        Instrument existing = instrumentBuilder().lastPrice(new BigDecimal("92.50")).build();
        given(instrumentRepository.findFirstByUserIdAndIsinIgnoreCase(USER_ID, ISIN))
                .willReturn(Optional.of(existing));
        stubSixRefreshReturnsInputUnchanged();
        Position existingPosition = Position.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .instrumentId(INSTRUMENT_ID)
                .quantity(new BigDecimal("1"))
                .purchasePrice(new BigDecimal("1.00"))
                .build();
        given(positionRepository.findByUserIdAndInstrumentId(USER_ID, INSTRUMENT_ID))
                .willReturn(Optional.of(existingPosition));
        stubPositionSaveEchoesArgument();

        // (1 x 1.00 + 2 x 2.00) / 3 = 1.666666… -> 1.6667 at scale 4 HALF_UP
        PositionResponse result = positionService.create(
                request(null, ISIN, null, "2", "2.00", null), USER_ID);

        assertThat(result.id()).isEqualTo(existingPosition.getId());
        assertThat(result.quantity()).isEqualByComparingTo("3");
        assertThat(result.purchasePrice()).isEqualTo(new BigDecimal("1.6667"));
    }

    // =========================================================================
    // create() — validation
    // =========================================================================

    @Test
    void create_throws_IllegalArgumentException_when_neither_name_nor_isin_nor_valor_is_given() {
        PositionRequest withoutIdentifier = request(null, " ", null, "10", "100.00", null);

        assertThatThrownBy(() -> positionService.create(withoutIdentifier, USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name, isin or valor");
        then(positionRepository).shouldHaveNoInteractions();
    }

    // =========================================================================
    // createBulk()
    // =========================================================================

    @Test
    void createBulk_imports_valid_rows_and_collects_errors_for_invalid_ones() {
        stubInstrumentSaveEchoesArgument();
        stubSixRefreshReturnsInputUnchanged();
        given(positionRepository.findByUserIdAndInstrumentId(any(), any()))
                .willReturn(Optional.empty());
        stubPositionSaveEchoesArgument();

        PositionBulkRequest bulk = new PositionBulkRequest(List.of(
                request("Fund A", null, null, "10", "100.00", null),
                request(null, null, null, "5", "50.00", null),      // no identifier
                request("Fund C", null, null, "-1", "10.00", null), // negative quantity
                request("Fund D", null, null, "2", "20.00", null)));

        BulkImportResultResponse result = positionService.createBulk(bulk, USER_ID);

        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(2);
        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors().get(0)).startsWith("Row 2:");
        assertThat(result.errors().get(1)).startsWith("Row 3:");
        then(positionRepository).should(times(2)).save(any(Position.class));
    }

    @Test
    void createBulk_keeps_other_rows_imported_when_one_row_fails_with_an_unexpected_error() {
        stubInstrumentSaveEchoesArgument();
        stubSixRefreshReturnsInputUnchanged();
        given(positionRepository.findByUserIdAndInstrumentId(any(), any()))
                .willReturn(Optional.empty());
        given(positionRepository.save(any(Position.class))).willAnswer(invocation -> {
            Position p = invocation.getArgument(0);
            if (new BigDecimal("99").compareTo(p.getQuantity()) == 0) {
                throw new DataIntegrityViolationException("db constraint violated");
            }
            return p;
        });

        PositionBulkRequest bulk = new PositionBulkRequest(List.of(
                request("Fund A", null, null, "10", "100.00", null),
                request("Fund B", null, null, "99", "50.00", null),   // save blows up
                request("Fund C", null, null, "2", "20.00", null)));

        BulkImportResultResponse result = positionService.createBulk(bulk, USER_ID);

        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).startsWith("Row 2:").contains("db constraint violated");
    }

    // =========================================================================
    // delete()
    // =========================================================================

    @Test
    void delete_removes_the_position_when_it_belongs_to_the_user() {
        UUID id = UUID.randomUUID();
        Position position = Position.builder().id(id).userId(USER_ID).build();
        given(positionRepository.findByIdAndUserId(id, USER_ID)).willReturn(Optional.of(position));

        positionService.delete(id, USER_ID);

        then(positionRepository).should().delete(position);
    }

    @Test
    void delete_never_deletes_when_position_belongs_to_another_user() {
        UUID id = UUID.randomUUID();
        given(positionRepository.findByIdAndUserId(id, "attacker")).willReturn(Optional.empty());

        assertThatThrownBy(() -> positionService.delete(id, "attacker"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Position");
        then(positionRepository).should(never()).delete(any(Position.class));
    }
}
