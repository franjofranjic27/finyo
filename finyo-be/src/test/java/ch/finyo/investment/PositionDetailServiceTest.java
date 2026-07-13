package ch.finyo.investment;

import ch.finyo.common.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

/**
 * Pure unit tests for PositionDetailService.
 *
 * Focus areas:
 *   1. Instrument PATCH semantics: name/assetClass only when present, valor/
 *      ter/factsheetUrl always (null/blank = clear); the stored factsheet
 *      PDF lives in its own table and is never touched by the patch.
 *   2. Holding PATCH semantics: quantity/purchasePrice/currentPrice only when
 *      present, purchaseDate always (null = clear, empty patch = 400);
 *      currentPrice writes through to the instrument's manual price.
 *   3. Presence-only validation: blank name, factsheet URL scheme.
 *   4. Factsheet upload validation: empty, oversized, non-PDF content,
 *      filename sanitization.
 *   5. Multi-tenancy: a foreign position id is always a 404 and never mutates.
 */
@ExtendWith(MockitoExtension.class)
class PositionDetailServiceTest {

    private static final String USER_ID = "user-detail-1";
    private static final UUID POSITION_ID = UUID.randomUUID();
    private static final UUID INSTRUMENT_ID = UUID.randomUUID();
    private static final byte[] PDF_BYTES =
            "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private InstrumentFactsheetRepository factsheetRepository;

    @Mock
    private PortfolioService portfolioService;

    @InjectMocks
    private PositionDetailService service;

    // -------------------------------------------------------------------------
    // Builders & stubbing helpers
    // -------------------------------------------------------------------------

    private Instrument existingInstrument() {
        return Instrument.builder()
                .id(INSTRUMENT_ID)
                .userId(USER_ID)
                .name("Original Name")
                .isin("CH0038863350")
                .valor("3886335")
                .assetClass(AssetClass.STOCK)
                .ter(new BigDecimal("0.50"))
                .factsheetUrl("https://example.com/old.pdf")
                .sortOrder(0)
                .build();
    }

    private InstrumentFactsheet storedFactsheet() {
        return InstrumentFactsheet.builder()
                .instrumentId(INSTRUMENT_ID)
                .userId(USER_ID)
                .pdf(PDF_BYTES)
                .filename("old.pdf")
                .size(PDF_BYTES.length)
                .uploadedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    private void stubOwnedPositionWithInstrument(Instrument instrument) {
        Position position = Position.builder()
                .id(POSITION_ID)
                .userId(USER_ID)
                .instrumentId(INSTRUMENT_ID)
                .quantity(BigDecimal.TEN)
                .purchasePrice(BigDecimal.ONE)
                .build();
        given(positionRepository.findByIdAndUserId(POSITION_ID, USER_ID)).willReturn(Optional.of(position));
        given(instrumentRepository.findByIdAndUserId(INSTRUMENT_ID, USER_ID)).willReturn(Optional.of(instrument));
    }

    private Position existingPosition() {
        return Position.builder()
                .id(POSITION_ID)
                .userId(USER_ID)
                .instrumentId(INSTRUMENT_ID)
                .quantity(BigDecimal.TEN)
                .purchasePrice(new BigDecimal("100"))
                .purchaseDate(LocalDate.of(2026, 1, 15))
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(30))
                .build();
    }

    // =========================================================================
    // updatePosition() — holding patch semantics
    // =========================================================================

    @Test
    void updatePosition_applies_only_present_fields_and_preserves_identity() {
        Position position = existingPosition();
        given(positionRepository.findByIdAndUserId(POSITION_ID, USER_ID)).willReturn(Optional.of(position));

        service.updatePosition(POSITION_ID,
                new PositionPatchRequest(new BigDecimal("5"), null, LocalDate.of(2026, 3, 1), null), USER_ID);

        then(positionRepository).should().save(argThat(saved ->
                new BigDecimal("5").compareTo(saved.getQuantity()) == 0
                        // purchasePrice absent from the patch — must survive
                        && new BigDecimal("100").compareTo(saved.getPurchasePrice()) == 0
                        && LocalDate.of(2026, 3, 1).equals(saved.getPurchaseDate())
                        && USER_ID.equals(saved.getUserId())
                        && INSTRUMENT_ID.equals(saved.getInstrumentId())
                        && position.getCreatedAt().equals(saved.getCreatedAt())));
        // no currentPrice in the patch — the instrument is never touched
        then(instrumentRepository).shouldHaveNoInteractions();
        then(portfolioService).should().getPositionDetail(POSITION_ID, USER_ID);
    }

    @Test
    void updatePosition_clears_the_purchase_date_when_another_field_is_present() {
        given(positionRepository.findByIdAndUserId(POSITION_ID, USER_ID))
                .willReturn(Optional.of(existingPosition()));

        service.updatePosition(POSITION_ID,
                new PositionPatchRequest(null, new BigDecimal("90"), null, null), USER_ID);

        then(positionRepository).should().save(argThat(saved ->
                saved.getPurchaseDate() == null
                        && new BigDecimal("90").compareTo(saved.getPurchasePrice()) == 0
                        && BigDecimal.TEN.compareTo(saved.getQuantity()) == 0));
    }

    @Test
    void updatePosition_rejects_an_all_null_patch() {
        // {} must be a 400 — clearing purchaseDate alone is indistinguishable
        // from an empty body and therefore requires another field.
        PositionPatchRequest patch = new PositionPatchRequest(null, null, null, null);

        assertThatThrownBy(() -> service.updatePosition(POSITION_ID, patch, USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one field");
        then(positionRepository).shouldHaveNoInteractions();
    }

    @Test
    void updatePosition_rejects_a_non_positive_quantity() {
        PositionPatchRequest patch = new PositionPatchRequest(BigDecimal.ZERO, null, null, null);

        assertThatThrownBy(() -> service.updatePosition(POSITION_ID, patch, USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
        then(positionRepository).shouldHaveNoInteractions();
    }

    @Test
    void updatePosition_writes_the_current_price_onto_the_instrument_with_a_timestamp() {
        given(positionRepository.findByIdAndUserId(POSITION_ID, USER_ID))
                .willReturn(Optional.of(existingPosition()));
        given(instrumentRepository.findByIdAndUserId(INSTRUMENT_ID, USER_ID))
                .willReturn(Optional.of(existingInstrument().toBuilder()
                        .lastPrice(new BigDecimal("80"))
                        .build()));

        service.updatePosition(POSITION_ID,
                new PositionPatchRequest(null, null, LocalDate.of(2026, 3, 1),
                        new BigDecimal("120")), USER_ID);

        // the explicit user edit always overwrites the stored manual price
        then(instrumentRepository).should().save(argThat(saved ->
                new BigDecimal("120").compareTo(saved.getLastPrice()) == 0
                        && saved.getLastPriceUpdatedAt() != null));
    }

    @Test
    void updatePosition_throws_404_for_a_foreign_position() {
        given(positionRepository.findByIdAndUserId(POSITION_ID, USER_ID)).willReturn(Optional.empty());
        PositionPatchRequest patch = new PositionPatchRequest(BigDecimal.ONE, null, null, null);

        assertThatThrownBy(() -> service.updatePosition(POSITION_ID, patch, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        then(positionRepository).should(never()).save(any());
        then(instrumentRepository).shouldHaveNoInteractions();
    }

    // =========================================================================
    // updateInstrument() — partial-update semantics
    // =========================================================================

    @Test
    void updateInstrument_keeps_name_and_asset_class_but_clears_omitted_optional_fields() {
        // The edit dialog sends the full field set; null valor/ter/factsheetUrl
        // is an explicit clear, while name/assetClass never become null.
        stubOwnedPositionWithInstrument(existingInstrument());

        service.updateInstrument(POSITION_ID,
                new InstrumentPatchRequest(null, null, null, new BigDecimal("0.20"), null), USER_ID);

        then(instrumentRepository).should().save(argThat(saved ->
                new BigDecimal("0.20").compareTo(saved.getTer()) == 0
                        && "Original Name".equals(saved.getName())
                        && saved.getAssetClass() == AssetClass.STOCK
                        && saved.getValor() == null
                        && saved.getFactsheetUrl() == null));
        // the stored factsheet PDF is outside the patch contract
        then(factsheetRepository).shouldHaveNoInteractions();
        then(portfolioService).should().getPositionDetail(POSITION_ID, USER_ID);
    }

    @Test
    void updateInstrument_treats_blank_valor_as_clear() {
        stubOwnedPositionWithInstrument(existingInstrument());

        service.updateInstrument(POSITION_ID,
                new InstrumentPatchRequest(null, null, "  ", null, null), USER_ID);

        then(instrumentRepository).should().save(argThat(saved ->
                saved.getValor() == null && saved.getTer() == null));
    }

    @Test
    void updateInstrument_applies_all_provided_fields() {
        stubOwnedPositionWithInstrument(existingInstrument());

        service.updateInstrument(POSITION_ID,
                new InstrumentPatchRequest("New Name", AssetClass.ETF, "1234567",
                        new BigDecimal("0.07"), "http://example.com/new.pdf"),
                USER_ID);

        then(instrumentRepository).should().save(argThat(saved ->
                "New Name".equals(saved.getName())
                        && saved.getAssetClass() == AssetClass.ETF
                        && "1234567".equals(saved.getValor())
                        && new BigDecimal("0.07").compareTo(saved.getTer()) == 0
                        && "http://example.com/new.pdf".equals(saved.getFactsheetUrl())
                        // untouched by the patch contract
                        && "CH0038863350".equals(saved.getIsin())));
    }

    @Test
    void updateInstrument_rejects_a_blank_name() {
        InstrumentPatchRequest patch = new InstrumentPatchRequest("   ", null, null, null, null);

        assertThatThrownBy(() -> service.updateInstrument(POSITION_ID, patch, USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
        then(instrumentRepository).shouldHaveNoInteractions();
    }

    @Test
    void updateInstrument_rejects_a_factsheet_url_without_http_scheme() {
        InstrumentPatchRequest patch =
                new InstrumentPatchRequest(null, null, null, null, "ftp://example.com/f.pdf");

        assertThatThrownBy(() -> service.updateInstrument(POSITION_ID, patch, USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("factsheetUrl");
        then(instrumentRepository).shouldHaveNoInteractions();
    }

    @Test
    void updateInstrument_throws_404_for_a_foreign_position() {
        given(positionRepository.findByIdAndUserId(POSITION_ID, USER_ID)).willReturn(Optional.empty());
        InstrumentPatchRequest patch = new InstrumentPatchRequest("New Name", null, null, null, null);

        assertThatThrownBy(() -> service.updateInstrument(POSITION_ID, patch, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        then(instrumentRepository).should(never()).save(any());
    }

    // =========================================================================
    // uploadFactsheet() — validation
    // =========================================================================

    @Test
    void uploadFactsheet_rejects_an_empty_file() {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.uploadFactsheet(POSITION_ID, empty, USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
        then(factsheetRepository).shouldHaveNoInteractions();
    }

    @Test
    void uploadFactsheet_rejects_files_larger_than_10_mb() {
        MultipartFile oversized = mock(MultipartFile.class);
        given(oversized.isEmpty()).willReturn(false);
        given(oversized.getSize()).willReturn(PositionDetailService.MAX_FACTSHEET_BYTES + 1);

        assertThatThrownBy(() -> service.uploadFactsheet(POSITION_ID, oversized, USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10 MB");
        then(factsheetRepository).shouldHaveNoInteractions();
    }

    @Test
    void uploadFactsheet_rejects_content_without_pdf_magic_bytes() {
        MockMultipartFile notAPdf = new MockMultipartFile("file", "evil.pdf", "application/pdf",
                "hello world".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> service.uploadFactsheet(POSITION_ID, notAPdf, USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PDF");
        then(factsheetRepository).shouldHaveNoInteractions();
    }

    @Test
    void uploadFactsheet_upserts_bytes_size_timestamp_and_the_sanitized_filename() {
        stubOwnedPositionWithInstrument(existingInstrument());
        MockMultipartFile file = new MockMultipartFile("file",
                "../uploads/Fact Sheet 2025.pdf", "application/pdf", PDF_BYTES);

        service.uploadFactsheet(POSITION_ID, file, USER_ID);

        then(factsheetRepository).should().upsert(
                eq(INSTRUMENT_ID),
                eq(USER_ID),
                eq(PDF_BYTES),
                eq("Fact Sheet 2025.pdf"),
                eq((long) PDF_BYTES.length),
                any(OffsetDateTime.class));
        // the upload never rewrites the instrument row itself
        then(instrumentRepository).should(never()).save(any());
        then(portfolioService).should().getPositionDetail(POSITION_ID, USER_ID);
    }

    // =========================================================================
    // sanitizeFilename()
    // =========================================================================

    @Test
    void sanitizeFilename_strips_path_components_and_header_breaking_characters() {
        assertThat(PositionDetailService.sanitizeFilename("../../etc/passwd.pdf")).isEqualTo("passwd.pdf");
        assertThat(PositionDetailService.sanitizeFilename("C:\\temp\\report.pdf")).isEqualTo("report.pdf");
        assertThat(PositionDetailService.sanitizeFilename("a\"b\r\nc.pdf")).isEqualTo("a_b__c.pdf");
    }

    @Test
    void sanitizeFilename_falls_back_to_a_default_and_caps_the_length() {
        assertThat(PositionDetailService.sanitizeFilename(null)).isEqualTo("factsheet.pdf");
        assertThat(PositionDetailService.sanitizeFilename("  ")).isEqualTo("factsheet.pdf");
        assertThat(PositionDetailService.sanitizeFilename("dir/")).isEqualTo("factsheet.pdf");
        assertThat(PositionDetailService.sanitizeFilename("x".repeat(300))).hasSize(255);
    }

    // =========================================================================
    // getFactsheet() / deleteFactsheet()
    // =========================================================================

    @Test
    void getFactsheet_returns_the_stored_bytes_and_filename() {
        stubOwnedPositionWithInstrument(existingInstrument());
        given(factsheetRepository.findByInstrumentIdAndUserId(INSTRUMENT_ID, USER_ID))
                .willReturn(Optional.of(storedFactsheet()));

        FactsheetDownload download = service.getFactsheet(POSITION_ID, USER_ID);

        assertThat(download.filename()).isEqualTo("old.pdf");
        assertThat(download.content()).isEqualTo(PDF_BYTES);
    }

    @Test
    void getFactsheet_throws_404_when_no_factsheet_is_stored() {
        stubOwnedPositionWithInstrument(existingInstrument());
        given(factsheetRepository.findByInstrumentIdAndUserId(INSTRUMENT_ID, USER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getFactsheet(POSITION_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteFactsheet_removes_the_stored_row_but_keeps_the_external_link() {
        stubOwnedPositionWithInstrument(existingInstrument());
        given(factsheetRepository.deleteByInstrumentIdAndUserId(INSTRUMENT_ID, USER_ID)).willReturn(1);

        service.deleteFactsheet(POSITION_ID, USER_ID);

        then(factsheetRepository).should().deleteByInstrumentIdAndUserId(INSTRUMENT_ID, USER_ID);
        // the external link is independent of the stored PDF
        then(instrumentRepository).should(never()).save(any());
    }

    @Test
    void deleteFactsheet_throws_404_when_no_factsheet_is_stored() {
        stubOwnedPositionWithInstrument(existingInstrument());
        given(factsheetRepository.deleteByInstrumentIdAndUserId(INSTRUMENT_ID, USER_ID)).willReturn(0);

        assertThatThrownBy(() -> service.deleteFactsheet(POSITION_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
