package ch.finyo.investment;

import ch.finyo.common.ResourceNotFoundException;
import ch.finyo.common.SourceResult;
import ch.finyo.common.money.CurrencyCode;
import ch.finyo.marketdata.MarketDataService;
import ch.finyo.marketdata.spi.DataSource;
import ch.finyo.marketdata.spi.SecurityReference;
import ch.finyo.marketdata.spi.SecurityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
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
import static org.mockito.BDDMockito.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.times;

/**
 * Unit tests for PositionService.
 *
 * Focus areas:
 * <ol>
 *   <li>Instrument resolution: reuse by ISIN (before valor), auto-creation via
 *       InstrumentFactory.</li>
 *   <li>Re-resolution: an instrument created while the providers were unreachable
 *       (source=UNRESOLVED) gets another attempt the next time it is touched — and one that
 *       was legitimately never known (HEURISTIC) does not. Without the first half a provider
 *       outage pins a guess to a security forever; without the second, every import of an
 *       unlisted 3a fund re-asks two vendors that will never know it.</li>
 *   <li>The initial price: a new holding is priced immediately rather than waiting for the
 *       nightly job — and the fetch happens <em>before</em> the transaction opens, because it
 *       is a network call and a database connection must not be held across one.</li>
 *   <li>currentPrice override, merge semantics (weighted average at scale 4), bulk import
 *       fault tolerance, multi-tenancy on delete.</li>
 * </ol>
 *
 * Both network calls are stubbed at their boundaries — InstrumentFactory for master data,
 * MarketDataService for the price. That they happen outside the transaction is a property of
 * the code under test, and it is the reason lookup() and create() are two calls rather than one.
 */
@DisplayName("PositionService")
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
    private MarketDataService marketData;

    @Mock
    private InstrumentFactory instrumentFactory;

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

    private static SourceResult<SecurityReference> resolvedByProvider() {
        return SourceResult.found(new SecurityReference(
                ISIN, VALOR, "NESN", "NESTLE N", SecurityType.EQUITY,
                new CurrencyCode("USD"), "Nestlé SA", DataSource.SIX,
                OffsetDateTime.now(ZoneOffset.UTC)));
    }

    /** No provider knew the security — the ordinary outcome for an unlisted 3a fund. */
    private void stubNoProviderKnowsTheSecurity() {
        given(instrumentFactory.lookup(any(), any())).willReturn(SourceResult.notFound());
    }

    /**
     * Auto-creation delegates the master-data lookup to InstrumentFactory. Here it stands in
     * as a pass-through that turns the request fields into a new (unsaved) instrument — what
     * the factory does with a provider hit or the name heuristic is InstrumentFactoryTest's
     * subject, not this one's.
     */
    private void stubInstrumentFactoryEchoesRequestFields() {
        given(instrumentFactory.create(any(), any(), any(), any(), any())).willAnswer(invocation ->
                Instrument.builder()
                        .name(invocation.getArgument(1))
                        .isin(invocation.getArgument(2))
                        .valor(invocation.getArgument(3))
                        .userId(invocation.getArgument(4))
                        .instrumentType(InstrumentType.OTHER)
                        .sortOrder(0)
                        .build());
    }

    /** save() assigns INSTRUMENT_ID on insert and echoes updates unchanged. */
    private void stubInstrumentSaveEchoesArgument() {
        given(instrumentRepository.save(any(Instrument.class))).willAnswer(invocation -> {
            Instrument i = invocation.getArgument(0);
            return i.getId() != null ? i : i.toBuilder().id(INSTRUMENT_ID).build();
        });
    }

    private void stubPositionSaveEchoesArgument() {
        given(positionRepository.save(any(Position.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubNoExistingPosition() {
        given(positionRepository.findByUserIdAndInstrumentId(USER_ID, INSTRUMENT_ID))
                .willReturn(Optional.empty());
    }

    // =========================================================================
    // create() — instrument resolution
    // =========================================================================

    @Nested
    @DisplayName("create — resolving the instrument")
    class InstrumentResolution {

        @Test
        void reuses_the_existing_instrument_found_by_isin() {
            Instrument existing = instrumentBuilder().lastPrice(new BigDecimal("92.50")).build();
            stubNoProviderKnowsTheSecurity();
            given(instrumentRepository.findFirstByUserIdAndIsinIgnoreCase(USER_ID, ISIN))
                    .willReturn(Optional.of(existing));
            stubNoExistingPosition();
            stubPositionSaveEchoesArgument();

            PositionResponse result = positionService.create(
                    request(null, ISIN, VALOR, "10", "80.00", null), USER_ID);

            assertThat(result.instrumentId()).isEqualTo(INSTRUMENT_ID);
            assertThat(result.name()).isEqualTo("Nestlé");
            then(instrumentRepository).should(never()).save(any());
            then(instrumentRepository).should(never()).findFirstByUserIdAndValor(any(), any());
        }

        @Test
        void falls_back_to_the_valor_lookup_when_the_isin_is_unknown() {
            Instrument existing = instrumentBuilder().isin(null).lastPrice(new BigDecimal("92.50")).build();
            stubNoProviderKnowsTheSecurity();
            given(instrumentRepository.findFirstByUserIdAndIsinIgnoreCase(USER_ID, ISIN))
                    .willReturn(Optional.empty());
            given(instrumentRepository.findFirstByUserIdAndValor(USER_ID, VALOR))
                    .willReturn(Optional.of(existing));
            stubNoExistingPosition();
            stubPositionSaveEchoesArgument();

            PositionResponse result = positionService.create(
                    request(null, ISIN, VALOR, "10", "80.00", null), USER_ID);

            assertThat(result.instrumentId()).isEqualTo(INSTRUMENT_ID);
            then(instrumentRepository).should(never()).save(any());
        }

        @Test
        void auto_creates_the_instrument_from_what_the_factory_resolved() {
            stubNoProviderKnowsTheSecurity();
            given(instrumentRepository.findFirstByUserIdAndIsinIgnoreCase(USER_ID, ISIN))
                    .willReturn(Optional.empty());
            stubInstrumentFactoryEchoesRequestFields();
            stubInstrumentSaveEchoesArgument();
            stubNoExistingPosition();
            stubPositionSaveEchoesArgument();

            positionService.create(request(null, ISIN, null, "10", "80.00", null), USER_ID);

            then(instrumentRepository).should().save(argThat(i ->
                    i.getId() == null && ISIN.equals(i.getIsin()) && USER_ID.equals(i.getUserId())));
        }

        @Test
        void resolves_the_master_data_before_it_opens_a_transaction() {
            // Not a style point. SecurityLookup makes HTTP calls; doing that inside the
            // transaction would hold a database connection open across a network round trip,
            // and ten concurrent creates against a hanging vendor would drain the pool and take
            // down endpoints that have nothing to do with investments. The lookup being its own
            // call — passed *into* the transactional work — is what makes the ordering
            // enforceable at all.
            stubNoProviderKnowsTheSecurity();
            given(instrumentRepository.findFirstByUserIdAndIsinIgnoreCase(USER_ID, ISIN))
                    .willReturn(Optional.empty());
            stubInstrumentFactoryEchoesRequestFields();
            stubInstrumentSaveEchoesArgument();
            stubNoExistingPosition();
            stubPositionSaveEchoesArgument();

            positionService.create(request(null, ISIN, VALOR, "10", "80.00", null), USER_ID);

            then(instrumentFactory).should().lookup(ISIN, VALOR);
        }
    }

    // =========================================================================
    // create() — the first price
    // =========================================================================

    @Nested
    @DisplayName("create — pricing the new holding straight away")
    class InitialPrice {

        @Test
        void refreshes_the_price_of_the_security_it_just_added() {
            // Without this the new position shows no market price until the nightly job runs,
            // which for a position created at 09:00 means a whole day of looking broken.
            stubNoProviderKnowsTheSecurity();
            given(instrumentRepository.findFirstByUserIdAndIsinIgnoreCase(USER_ID, ISIN))
                    .willReturn(Optional.empty());
            stubInstrumentFactoryEchoesRequestFields();
            stubInstrumentSaveEchoesArgument();
            stubNoExistingPosition();
            stubPositionSaveEchoesArgument();

            positionService.create(request(null, ISIN, null, "10", "80.00", null), USER_ID);

            then(marketData).should().refresh(List.of(ISIN));
        }

        @Test
        void fetches_the_price_before_the_transaction_opens() {
            // Same reason as the master-data lookup: refresh() goes to SIX over HTTP. Inside
            // the transaction it would pin a database connection for the length of the round
            // trip — and the whole PR is about getting vendor latency out of the connection
            // pool's way. The first database write of the create path is the instrument save,
            // so refresh must come first.
            stubNoProviderKnowsTheSecurity();
            given(instrumentRepository.findFirstByUserIdAndIsinIgnoreCase(USER_ID, ISIN))
                    .willReturn(Optional.empty());
            stubInstrumentFactoryEchoesRequestFields();
            stubInstrumentSaveEchoesArgument();
            stubNoExistingPosition();
            stubPositionSaveEchoesArgument();

            positionService.create(request(null, ISIN, null, "10", "80.00", null), USER_ID);

            InOrder order = Mockito.inOrder(instrumentFactory, marketData, transactionManager);
            order.verify(instrumentFactory).lookup(ISIN, null);
            order.verify(marketData).refresh(List.of(ISIN));
            order.verify(transactionManager).getTransaction(any());
        }

        @Test
        void asks_for_no_price_when_the_holding_has_no_isin_to_ask_about() {
            // A position entered by name only ("Notgroschen") has nothing to price. Calling a
            // rate-limited vendor with nothing to ask about is pure waste.
            stubNoProviderKnowsTheSecurity();
            stubInstrumentFactoryEchoesRequestFields();
            stubInstrumentSaveEchoesArgument();
            stubNoExistingPosition();
            stubPositionSaveEchoesArgument();

            positionService.create(request("Notgroschen", null, null, "10", "80.00", null), USER_ID);

            then(marketData).shouldHaveNoInteractions();
        }

        @Test
        void still_creates_the_position_when_no_price_could_be_fetched() {
            // Best-effort by design: an unreachable provider writes nothing, and the position
            // is created anyway. It simply shows no market price until the next sync — which
            // the UI states rather than hides.
            stubNoProviderKnowsTheSecurity();
            given(instrumentRepository.findFirstByUserIdAndIsinIgnoreCase(USER_ID, ISIN))
                    .willReturn(Optional.empty());
            given(marketData.refresh(List.of(ISIN))).willReturn(0);
            stubInstrumentFactoryEchoesRequestFields();
            stubInstrumentSaveEchoesArgument();
            stubNoExistingPosition();
            stubPositionSaveEchoesArgument();

            PositionResponse result = positionService.create(
                    request(null, ISIN, null, "10", "80.00", null), USER_ID);

            assertThat(result.instrumentId()).isEqualTo(INSTRUMENT_ID);
            assertThat(result.quantity()).isEqualByComparingTo("10");
        }
    }

    // =========================================================================
    // create() — an UNRESOLVED instrument gets another chance
    // =========================================================================

    @Nested
    @DisplayName("create — the second chance an UNRESOLVED instrument gets")
    class ReResolution {

        @Test
        void re_resolves_an_instrument_that_was_created_while_the_providers_were_down() {
            // The instrument exists, so nothing would ever look it up again — which is exactly
            // how a five-minute outage used to pin a name-derived guess to a real security for
            // good. UNRESOLVED marks it as unfinished business, and touching it is the trigger.
            Instrument unresolved = instrumentBuilder().source(DataSource.UNRESOLVED).build();
            Instrument enriched = unresolved.toBuilder()
                    .currency(new CurrencyCode("USD"))
                    .source(DataSource.SIX)
                    .build();
            SourceResult<SecurityReference> found = resolvedByProvider();
            given(instrumentFactory.lookup(any(), any())).willReturn(found);
            given(instrumentRepository.findFirstByUserIdAndIsinIgnoreCase(USER_ID, ISIN))
                    .willReturn(Optional.of(unresolved));
            given(instrumentFactory.enrich(unresolved, found)).willReturn(Optional.of(enriched));
            given(instrumentRepository.save(enriched)).willReturn(enriched);
            stubNoExistingPosition();
            stubPositionSaveEchoesArgument();

            positionService.create(request(null, ISIN, null, "10", "80.00", null), USER_ID);

            then(instrumentRepository).should().save(argThat(i ->
                    i.getSource() == DataSource.SIX && new CurrencyCode("USD").equals(i.getCurrency())));
        }

        @Test
        void keeps_an_UNRESOLVED_instrument_as_it_is_when_the_providers_are_still_down() {
            Instrument unresolved = instrumentBuilder().source(DataSource.UNRESOLVED).build();
            SourceResult<SecurityReference> stillDown = SourceResult.unavailable("six: read timed out");
            given(instrumentFactory.lookup(any(), any())).willReturn(stillDown);
            given(instrumentRepository.findFirstByUserIdAndIsinIgnoreCase(USER_ID, ISIN))
                    .willReturn(Optional.of(unresolved));
            given(instrumentFactory.enrich(unresolved, stillDown)).willReturn(Optional.empty());
            stubNoExistingPosition();
            stubPositionSaveEchoesArgument();

            positionService.create(request(null, ISIN, null, "10", "80.00", null), USER_ID);

            // No write, and — the point — the instrument keeps source=UNRESOLVED, so it is
            // still on the to-do list for the next import.
            then(instrumentRepository).should(never()).save(any(Instrument.class));
        }

        @Test
        void does_not_re_resolve_a_HEURISTIC_instrument() {
            // HEURISTIC is a settled answer: every provider was asked and none knew the
            // security. That is the normal, permanent state of an unlisted 3a fund, and
            // re-asking two rate-limited vendors about it on every single import would be pure
            // waste on sources we are merely tolerated on.
            Instrument heuristic = instrumentBuilder().source(DataSource.HEURISTIC).build();
            stubNoProviderKnowsTheSecurity();
            given(instrumentRepository.findFirstByUserIdAndIsinIgnoreCase(USER_ID, ISIN))
                    .willReturn(Optional.of(heuristic));
            stubNoExistingPosition();
            stubPositionSaveEchoesArgument();

            positionService.create(request(null, ISIN, null, "10", "80.00", null), USER_ID);

            then(instrumentFactory).should(never()).enrich(any(), any());
            then(instrumentRepository).should(never()).save(any(Instrument.class));
        }

        @Test
        void does_not_re_resolve_an_instrument_a_provider_already_verified() {
            Instrument verified = instrumentBuilder().source(DataSource.SIX).build();
            stubNoProviderKnowsTheSecurity();
            given(instrumentRepository.findFirstByUserIdAndIsinIgnoreCase(USER_ID, ISIN))
                    .willReturn(Optional.of(verified));
            stubNoExistingPosition();
            stubPositionSaveEchoesArgument();

            positionService.create(request(null, ISIN, null, "10", "80.00", null), USER_ID);

            then(instrumentFactory).should(never()).enrich(any(), any());
        }
    }

    // =========================================================================
    // create() — currentPrice override
    // =========================================================================

    @Nested
    @DisplayName("create — the manual price")
    class CurrentPriceOverride {

        @Test
        void applies_the_currentPrice_override_when_no_price_exists() {
            stubNoProviderKnowsTheSecurity();
            stubInstrumentFactoryEchoesRequestFields();
            stubInstrumentSaveEchoesArgument();
            stubNoExistingPosition();
            stubPositionSaveEchoesArgument();

            positionService.create(request("Manual Fund", null, null, "10", "100.00", "110.00"), USER_ID);

            // one save for the auto-creation, one for the price override
            then(instrumentRepository).should(times(2)).save(any(Instrument.class));
            then(instrumentRepository).should().save(argThat(i ->
                    i.getId() != null
                            && i.getLastPrice() != null
                            && new BigDecimal("110.00").compareTo(i.getLastPrice()) == 0
                            && i.getLastPriceUpdatedAt() != null));
        }

        @Test
        void ignores_the_currentPrice_override_when_a_price_already_exists() {
            Instrument existing = instrumentBuilder().lastPrice(new BigDecimal("92.50")).build();
            stubNoProviderKnowsTheSecurity();
            given(instrumentRepository.findFirstByUserIdAndIsinIgnoreCase(USER_ID, ISIN))
                    .willReturn(Optional.of(existing));
            stubNoExistingPosition();
            stubPositionSaveEchoesArgument();

            positionService.create(request(null, ISIN, null, "10", "80.00", "50.00"), USER_ID);

            then(instrumentRepository).should(never()).save(any());
        }
    }

    // =========================================================================
    // create() — merge semantics & validation
    // =========================================================================

    @Test
    void create_merges_into_an_existing_position_with_a_weighted_average_price() {
        Instrument existing = instrumentBuilder().lastPrice(new BigDecimal("92.50")).build();
        stubNoProviderKnowsTheSecurity();
        given(instrumentRepository.findFirstByUserIdAndIsinIgnoreCase(USER_ID, ISIN))
                .willReturn(Optional.of(existing));
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

    @Test
    void create_throws_IllegalArgumentException_when_neither_name_nor_isin_nor_valor_is_given() {
        PositionRequest withoutIdentifier = request(null, " ", null, "10", "100.00", null);

        assertThatThrownBy(() -> positionService.create(withoutIdentifier, USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name, isin or valor");
        then(positionRepository).shouldHaveNoInteractions();
        // Validation comes first: an invalid row must not cost a provider round trip, for
        // master data or for a price.
        then(instrumentFactory).shouldHaveNoInteractions();
        then(marketData).shouldHaveNoInteractions();
    }

    // =========================================================================
    // createBulk()
    // =========================================================================

    @Nested
    @DisplayName("createBulk — one bad row must not lose the good ones")
    class BulkImport {

        @Test
        void imports_valid_rows_and_collects_errors_for_invalid_ones() {
            stubNoProviderKnowsTheSecurity();
            stubInstrumentFactoryEchoesRequestFields();
            stubInstrumentSaveEchoesArgument();
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
        void keeps_other_rows_imported_when_one_row_fails_with_an_unexpected_error() {
            stubNoProviderKnowsTheSecurity();
            stubInstrumentFactoryEchoesRequestFields();
            stubInstrumentSaveEchoesArgument();
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
            assertThat(result.errors().getFirst()).startsWith("Row 2:").contains("db constraint violated");
        }

        @Test
        void resolves_and_prices_every_row_it_imports() {
            // Each row is looked up and priced before its own transaction opens, not once for
            // the batch: the rows are different securities.
            stubNoProviderKnowsTheSecurity();
            stubInstrumentFactoryEchoesRequestFields();
            stubInstrumentSaveEchoesArgument();
            given(positionRepository.findByUserIdAndInstrumentId(any(), any()))
                    .willReturn(Optional.empty());
            stubPositionSaveEchoesArgument();
            given(instrumentRepository.findFirstByUserIdAndIsinIgnoreCase(any(), any()))
                    .willReturn(Optional.empty());

            positionService.createBulk(new PositionBulkRequest(List.of(
                    request("Nestlé", ISIN, null, "10", "100.00", null),
                    request("iShares", "IE00B4L5Y983", null, "2", "20.00", null))), USER_ID);

            then(instrumentFactory).should().lookup(eq(ISIN), any());
            then(instrumentFactory).should().lookup(eq("IE00B4L5Y983"), any());
            then(marketData).should().refresh(List.of(ISIN));
            then(marketData).should().refresh(List.of("IE00B4L5Y983"));
        }
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
    void delete_never_deletes_when_the_position_belongs_to_another_user() {
        UUID id = UUID.randomUUID();
        given(positionRepository.findByIdAndUserId(id, "attacker")).willReturn(Optional.empty());

        assertThatThrownBy(() -> positionService.delete(id, "attacker"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Position");
        then(positionRepository).should(never()).delete(any(Position.class));
    }
}
