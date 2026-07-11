package ch.finyo.pillar3;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.times;

/**
 * Unit tests for Pillar3ProductService with a mocked repository and a real
 * jakarta Validator — the import endpoint validates rows programmatically
 * (row isolation) instead of relying on cascading @Valid, so the real
 * constraint definitions on Pillar3ProductRequest must be exercised here.
 */
@ExtendWith(MockitoExtension.class)
class Pillar3ProductServiceTest {

    private static final String NORMALIZED_ISIN = "CH0123456789";

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @Mock
    private Pillar3ProductRepository productRepository;

    private Pillar3ProductService productService;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        productService = new Pillar3ProductService(productRepository, validator);
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private Pillar3ProductRequest request(String isin) {
        return namedRequest(isin, "Vitainvest 100 ESG");
    }

    private Pillar3ProductRequest namedRequest(String isin, String name) {
        return new Pillar3ProductRequest("UBS", name, isin, "12345678",
                new BigDecimal("100"), new BigDecimal("0.50"), null, null);
    }

    private Pillar3Product existingProduct(UUID id, String isin) {
        return Pillar3Product.builder()
                .id(id)
                .provider("Old Provider")
                .name("Old Name")
                .isin(isin)
                .valor("9999999")
                .equityPct(new BigDecimal("45"))
                .terPct(new BigDecimal("1.00"))
                .active(false)
                .sortOrder(5)
                .build();
    }

    private void saveReturnsItsArgument() {
        given(productRepository.save(any(Pillar3Product.class))).willAnswer(inv -> inv.getArgument(0));
    }

    private Pillar3Product capturedSave() {
        ArgumentCaptor<Pillar3Product> captor = ArgumentCaptor.forClass(Pillar3Product.class);
        then(productRepository).should().save(captor.capture());
        return captor.getValue();
    }

    // =========================================================================
    // Create
    // =========================================================================

    @Test
    void create_normalizes_the_isin_to_trimmed_uppercase() {
        given(productRepository.findByIsin(NORMALIZED_ISIN)).willReturn(Optional.empty());
        saveReturnsItsArgument();

        Pillar3ProductResponse response = productService.create(request(" ch0123456789 "));

        assertThat(response.isin()).isEqualTo(NORMALIZED_ISIN);
        // The duplicate check must also run against the normalized ISIN,
        // otherwise case variants would bypass the UNIQUE constraint.
        then(productRepository).should().findByIsin(NORMALIZED_ISIN);
    }

    @Test
    void create_defaults_active_to_true_and_sort_order_to_zero_when_omitted() {
        given(productRepository.findByIsin(NORMALIZED_ISIN)).willReturn(Optional.empty());
        saveReturnsItsArgument();

        productService.create(request(NORMALIZED_ISIN));

        Pillar3Product saved = capturedSave();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getSortOrder()).isZero();
    }

    @Test
    void create_with_an_existing_isin_throws_illegal_state_naming_the_isin() {
        given(productRepository.findByIsin(NORMALIZED_ISIN))
                .willReturn(Optional.of(existingProduct(UUID.randomUUID(), NORMALIZED_ISIN)));
        Pillar3ProductRequest duplicate = request("ch0123456789");

        assertThatThrownBy(() -> productService.create(duplicate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(NORMALIZED_ISIN);
        then(productRepository).should(never()).save(any());
    }

    // =========================================================================
    // Update
    // =========================================================================

    @Test
    void update_normalizes_the_isin_to_trimmed_uppercase() {
        UUID id = UUID.randomUUID();
        given(productRepository.findById(id)).willReturn(Optional.of(existingProduct(id, "CH9999999999")));
        given(productRepository.findByIsin(NORMALIZED_ISIN)).willReturn(Optional.empty());
        saveReturnsItsArgument();

        Pillar3ProductResponse response = productService.update(id, request(" ch0123456789 "));

        assertThat(response.isin()).isEqualTo(NORMALIZED_ISIN);
    }

    @Test
    void update_with_the_isin_of_a_different_product_throws_illegal_state() {
        UUID id = UUID.randomUUID();
        given(productRepository.findById(id)).willReturn(Optional.of(existingProduct(id, "CH9999999999")));
        given(productRepository.findByIsin(NORMALIZED_ISIN))
                .willReturn(Optional.of(existingProduct(UUID.randomUUID(), NORMALIZED_ISIN)));
        Pillar3ProductRequest colliding = request(NORMALIZED_ISIN);

        assertThatThrownBy(() -> productService.update(id, colliding))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(NORMALIZED_ISIN);
        then(productRepository).should(never()).save(any());
    }

    @Test
    void update_keeping_its_own_isin_succeeds() {
        UUID id = UUID.randomUUID();
        given(productRepository.findById(id)).willReturn(Optional.of(existingProduct(id, NORMALIZED_ISIN)));
        // findByIsin resolves to the product being updated itself → no collision
        given(productRepository.findByIsin(NORMALIZED_ISIN))
                .willReturn(Optional.of(existingProduct(id, NORMALIZED_ISIN)));
        saveReturnsItsArgument();

        Pillar3ProductResponse response = productService.update(id, namedRequest(NORMALIZED_ISIN, "Renamed Fund"));

        assertThat(response.name()).isEqualTo("Renamed Fund");
        assertThat(response.isin()).isEqualTo(NORMALIZED_ISIN);
    }

    // =========================================================================
    // Import
    // =========================================================================

    @Test
    void import_counts_created_and_updated_rows_by_isin() {
        given(productRepository.findByIsin("CH1111111116")).willReturn(Optional.empty());
        given(productRepository.findByIsin("CH2222222226"))
                .willReturn(Optional.of(existingProduct(UUID.randomUUID(), "CH2222222226")));
        saveReturnsItsArgument();

        Pillar3ProductImportResult result = productService.importProducts(new Pillar3ProductImportRequest(
                List.of(request("CH1111111116"), request("CH2222222226"))));

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void import_isolates_an_invalid_row_and_reports_it_with_its_row_number() {
        given(productRepository.findByIsin("CH1111111116")).willReturn(Optional.empty());
        given(productRepository.findByIsin("CH3333333336")).willReturn(Optional.empty());
        saveReturnsItsArgument();

        Pillar3ProductImportResult result = productService.importProducts(new Pillar3ProductImportRequest(
                List.of(request("CH1111111116"), request("NOT_AN_ISIN"), request("CH3333333336"))));

        assertThat(result.created()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("row 2").contains("isin");
    }

    @Test
    void import_normalizes_the_isin_so_case_variants_update_the_existing_product() {
        // Note: unlike create/update, import validates each row BEFORE normalization,
        // so only case variants (not whitespace-padded ISINs) pass the @Pattern here.
        given(productRepository.findByIsin("CH2222222226"))
                .willReturn(Optional.of(existingProduct(UUID.randomUUID(), "CH2222222226")));
        saveReturnsItsArgument();

        Pillar3ProductImportResult result = productService.importProducts(new Pillar3ProductImportRequest(
                List.of(request("ch2222222226"))));

        assertThat(result.created()).isZero();
        assertThat(result.updated()).isEqualTo(1);
        then(productRepository).should().findByIsin("CH2222222226");
    }

    @Test
    void import_preserves_active_and_sort_order_of_the_existing_product_when_omitted() {
        // existingProduct() is inactive with sortOrder 5; the row carries neither field
        given(productRepository.findByIsin(NORMALIZED_ISIN))
                .willReturn(Optional.of(existingProduct(UUID.randomUUID(), NORMALIZED_ISIN)));
        saveReturnsItsArgument();

        productService.importProducts(new Pillar3ProductImportRequest(List.of(request(NORMALIZED_ISIN))));

        Pillar3Product saved = capturedSave();
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.getSortOrder()).isEqualTo(5);
    }

    @Test
    void import_overwrites_active_and_sort_order_when_provided() {
        given(productRepository.findByIsin(NORMALIZED_ISIN))
                .willReturn(Optional.of(existingProduct(UUID.randomUUID(), NORMALIZED_ISIN)));
        saveReturnsItsArgument();
        Pillar3ProductRequest row = new Pillar3ProductRequest("UBS", "Vitainvest 100 ESG",
                NORMALIZED_ISIN, "12345678", new BigDecimal("100"), new BigDecimal("0.50"), true, 9);

        productService.importProducts(new Pillar3ProductImportRequest(List.of(row)));

        Pillar3Product saved = capturedSave();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getSortOrder()).isEqualTo(9);
    }

    @Test
    void import_resolves_duplicate_isins_within_one_payload_last_wins() {
        UUID persistedId = UUID.randomUUID();
        // Row 1 creates the product, row 2 finds it and updates it (last wins).
        given(productRepository.findByIsin("CH1111111116"))
                .willReturn(Optional.empty(), Optional.of(existingProduct(persistedId, "CH1111111116")));
        saveReturnsItsArgument();

        Pillar3ProductImportResult result = productService.importProducts(new Pillar3ProductImportRequest(
                List.of(namedRequest("CH1111111116", "First"), namedRequest("CH1111111116", "Second"))));

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        ArgumentCaptor<Pillar3Product> captor = ArgumentCaptor.forClass(Pillar3Product.class);
        then(productRepository).should(times(2)).save(captor.capture());
        Pillar3Product lastSaved = captor.getAllValues().get(1);
        assertThat(lastSaved.getName()).isEqualTo("Second");
        assertThat(lastSaved.getId()).isEqualTo(persistedId);
    }
}
