package ch.finyo.tax;

import ch.finyo.common.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * Pure unit tests for TaxYearService.
 *
 * No Spring context is started. All repositories and the TaxCalculationService
 * are mocked; the calculation result is a canned TaxResultResponse so these
 * tests only verify the ORCHESTRATION logic of TaxYearService:
 *
 *   1. Upsert semantics — create when absent, update in place when present,
 *      always preserving userId and lifecycle fields (status, filedAt, ...).
 *   2. Status state machine — forward any number of steps, backward exactly
 *      one step, with filedAt/assessedAt stamped and cleared correctly.
 *   3. Derived figures — expectedTax source selection (calculation vs.
 *      assessedAmount) and openAmount arithmetic including negative refunds.
 *   4. Ownership — payments under a missing year or foreign payment ids
 *      surface as ResourceNotFoundException (mapped to 404), never as writes.
 */
@ExtendWith(MockitoExtension.class)
class TaxYearServiceTest {

    private static final String USER_ID = "user-1";
    private static final int YEAR = 2025;
    private static final UUID YEAR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private TaxYearRepository taxYearRepository;

    @Mock
    private TaxPaymentRepository taxPaymentRepository;

    @Mock
    private TaxDeadlineRepository taxDeadlineRepository;

    @Mock
    private TaxCalculationService taxCalculationService;

    @InjectMocks
    private TaxYearService service;

    // -------------------------------------------------------------------------
    // Builders / canned data
    // -------------------------------------------------------------------------

    /** A year whose inputs are complete enough for recompute() to run. */
    private TaxYear completeYear(TaxYearStatus status) {
        return TaxYear.builder()
                .id(YEAR_ID)
                .userId(USER_ID)
                .taxYear(YEAR)
                .status(status)
                .cantonCode("SG")
                .civilStatus(TaxCivilStatus.SINGLE)
                .grossEmploymentIncome(new BigDecimal("100000"))
                .build();
    }

    /** A year missing cantonCode/civilStatus/income — recompute() must yield null. */
    private TaxYear incompleteYear(TaxYearStatus status) {
        return TaxYear.builder()
                .id(YEAR_ID)
                .userId(USER_ID)
                .taxYear(YEAR)
                .status(status)
                .build();
    }

    private TaxResultResponse cannedResult(String grandTotal) {
        return new TaxResultResponse(
                YEAR, "SG", "SG", TaxCivilStatus.SINGLE,
                new BigDecimal("100000"), new BigDecimal("10000"), new BigDecimal("90000"),
                new BigDecimal("5000.00"), new BigDecimal("4000.00"), new BigDecimal("2000.00"),
                BigDecimal.ZERO, new BigDecimal("11000.00"),
                11.0, 20.0,
                BigDecimal.ZERO, new BigDecimal(grandTotal),
                BigDecimal.ZERO, List.of());
    }

    private TaxYearRequest completeRequest() {
        return new TaxYearRequest("SG", 3203, TaxCivilStatus.SINGLE, 0, ChurchAffiliation.NONE,
                new BigDecimal("100000"), null, null, null,
                null, null, null, null,
                new BigDecimal("7000"), null,
                LocalDate.of(2026, 3, 31), null);
    }

    private TaxPayment payment(BigDecimal amount) {
        return TaxPayment.builder()
                .id(UUID.randomUUID())
                .taxYearId(YEAR_ID)
                .userId(USER_ID)
                .paymentDate(LocalDate.of(2025, 6, 30))
                .amount(amount)
                .build();
    }

    // -------------------------------------------------------------------------
    // Common stubs
    // -------------------------------------------------------------------------

    private void stubEmptyPaymentsAndDeadlines() {
        given(taxPaymentRepository.findByTaxYearIdAndUserIdOrderByPaymentDateAsc(YEAR_ID, USER_ID))
                .willReturn(List.of());
        given(taxDeadlineRepository.findByTaxYearIdAndUserIdOrderByDueDateAsc(YEAR_ID, USER_ID))
                .willReturn(List.of());
    }

    private void stubSaveReturnsArgument() {
        given(taxYearRepository.save(any(TaxYear.class))).willAnswer(inv -> inv.getArgument(0));
    }

    /** Save assigns the well-known YEAR_ID, mimicking JPA id generation on insert. */
    private void stubSaveAssignsId() {
        given(taxYearRepository.save(any(TaxYear.class))).willAnswer(inv -> {
            TaxYear arg = inv.getArgument(0);
            return arg.getId() != null ? arg : arg.toBuilder().id(YEAR_ID).build();
        });
    }

    // =========================================================================
    // upsert()
    // =========================================================================

    @Test
    void upsert_creates_new_year_with_open_status_when_absent() {
        given(taxYearRepository.findByUserIdAndTaxYear(USER_ID, YEAR)).willReturn(Optional.empty());
        stubSaveAssignsId();
        given(taxCalculationService.calculate(any(TaxInputRequest.class)))
                .willReturn(cannedResult("12000.00"));
        stubEmptyPaymentsAndDeadlines();

        TaxYearDetailResponse result = service.upsert(YEAR, completeRequest(), USER_ID);

        assertThat(result.year()).isEqualTo(YEAR);
        assertThat(result.status()).isEqualTo(TaxYearStatus.OPEN);
        then(taxYearRepository).should().save(argThat(saved ->
                USER_ID.equals(saved.getUserId())
                        && saved.getTaxYear() == YEAR
                        && saved.getStatus() == TaxYearStatus.OPEN
                        && "SG".equals(saved.getCantonCode())));
    }

    @Test
    void upsert_updates_existing_year_preserving_userId_status_and_filedAt() {
        LocalDate filedAt = LocalDate.of(2026, 4, 1);
        TaxYear existing = completeYear(TaxYearStatus.FILED).toBuilder().filedAt(filedAt).build();
        given(taxYearRepository.findByUserIdAndTaxYear(USER_ID, YEAR)).willReturn(Optional.of(existing));
        stubSaveReturnsArgument();
        given(taxCalculationService.calculate(any(TaxInputRequest.class)))
                .willReturn(cannedResult("12000.00"));
        stubEmptyPaymentsAndDeadlines();

        TaxYearDetailResponse result = service.upsert(YEAR, completeRequest(), USER_ID);

        // Inputs are replaced, but identity and lifecycle fields survive the upsert
        assertThat(result.status()).isEqualTo(TaxYearStatus.FILED);
        assertThat(result.filedAt()).isEqualTo(filedAt);
        then(taxYearRepository).should().save(argThat(saved ->
                USER_ID.equals(saved.getUserId())
                        && YEAR_ID.equals(saved.getId())
                        && saved.getStatus() == TaxYearStatus.FILED));
    }

    @Test
    void upsert_preserves_filingDeadline_and_assessedAmount_when_request_omits_them() {
        LocalDate filingDeadline = LocalDate.of(2026, 3, 31);
        TaxYear existing = completeYear(TaxYearStatus.ASSESSED).toBuilder()
                .filingDeadline(filingDeadline)
                .assessedAmount(new BigDecimal("9999.99"))
                .build();
        given(taxYearRepository.findByUserIdAndTaxYear(USER_ID, YEAR)).willReturn(Optional.of(existing));
        stubSaveReturnsArgument();
        given(taxCalculationService.calculate(any(TaxInputRequest.class)))
                .willReturn(cannedResult("12000.00"));
        stubEmptyPaymentsAndDeadlines();
        // The calculator payload never carries filingDeadline/assessedAmount
        TaxYearRequest request = new TaxYearRequest("SG", 3203, TaxCivilStatus.SINGLE, 0, ChurchAffiliation.NONE,
                new BigDecimal("100000"), null, null, null,
                null, null, null, null,
                new BigDecimal("7000"), null,
                null, null);

        TaxYearDetailResponse result = service.upsert(YEAR, request, USER_ID);

        // A recalculation must never wipe the separately maintained lifecycle values
        assertThat(result.filingDeadline()).isEqualTo(filingDeadline);
        assertThat(result.assessedAmount()).isEqualByComparingTo("9999.99");
    }

    @Test
    void upsert_overwrites_filingDeadline_and_assessedAmount_when_request_provides_them() {
        TaxYear existing = completeYear(TaxYearStatus.ASSESSED).toBuilder()
                .filingDeadline(LocalDate.of(2026, 3, 31))
                .assessedAmount(new BigDecimal("9999.99"))
                .build();
        given(taxYearRepository.findByUserIdAndTaxYear(USER_ID, YEAR)).willReturn(Optional.of(existing));
        stubSaveReturnsArgument();
        given(taxCalculationService.calculate(any(TaxInputRequest.class)))
                .willReturn(cannedResult("12000.00"));
        stubEmptyPaymentsAndDeadlines();
        LocalDate newDeadline = LocalDate.of(2026, 6, 30);
        TaxYearRequest request = new TaxYearRequest("SG", 3203, TaxCivilStatus.SINGLE, 0, ChurchAffiliation.NONE,
                new BigDecimal("100000"), null, null, null,
                null, null, null, null,
                new BigDecimal("7000"), null,
                newDeadline, new BigDecimal("11111.11"));

        TaxYearDetailResponse result = service.upsert(YEAR, request, USER_ID);

        assertThat(result.filingDeadline()).isEqualTo(newDeadline);
        assertThat(result.assessedAmount()).isEqualByComparingTo("11111.11");
    }

    // =========================================================================
    // getByYear()
    // =========================================================================

    @Test
    void getByYear_throws_ResourceNotFoundException_when_year_absent() {
        given(taxYearRepository.findByUserIdAndTaxYear(USER_ID, YEAR)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByYear(YEAR, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tax year");
    }

    // =========================================================================
    // updateStatus() — state machine
    // =========================================================================

    @Test
    void updateStatus_open_to_filed_sets_filedAt_to_the_effective_date() {
        LocalDate effectiveDate = LocalDate.of(2026, 4, 15);
        given(taxYearRepository.findByUserIdAndTaxYear(USER_ID, YEAR))
                .willReturn(Optional.of(incompleteYear(TaxYearStatus.OPEN)));
        stubSaveReturnsArgument();
        stubEmptyPaymentsAndDeadlines();

        TaxYearDetailResponse result = service.updateStatus(
                YEAR, new TaxYearStatusRequest(TaxYearStatus.FILED, effectiveDate), USER_ID);

        assertThat(result.status()).isEqualTo(TaxYearStatus.FILED);
        assertThat(result.filedAt()).isEqualTo(effectiveDate);
        assertThat(result.assessedAt()).isNull();
    }

    @Test
    void updateStatus_defaults_effective_date_to_today_when_not_provided() {
        given(taxYearRepository.findByUserIdAndTaxYear(USER_ID, YEAR))
                .willReturn(Optional.of(incompleteYear(TaxYearStatus.OPEN)));
        stubSaveReturnsArgument();
        stubEmptyPaymentsAndDeadlines();
        LocalDate before = LocalDate.now();

        TaxYearDetailResponse result = service.updateStatus(
                YEAR, new TaxYearStatusRequest(TaxYearStatus.FILED, null), USER_ID);

        // isBetween guards against the (unlikely) midnight rollover during the test
        assertThat(result.filedAt()).isBetween(before, LocalDate.now());
    }

    @Test
    void updateStatus_forward_skip_open_to_assessed_stamps_filedAt_and_assessedAt() {
        LocalDate effectiveDate = LocalDate.of(2026, 9, 1);
        given(taxYearRepository.findByUserIdAndTaxYear(USER_ID, YEAR))
                .willReturn(Optional.of(incompleteYear(TaxYearStatus.OPEN)));
        stubSaveReturnsArgument();
        stubEmptyPaymentsAndDeadlines();

        TaxYearDetailResponse result = service.updateStatus(
                YEAR, new TaxYearStatusRequest(TaxYearStatus.ASSESSED, effectiveDate), USER_ID);

        assertThat(result.status()).isEqualTo(TaxYearStatus.ASSESSED);
        assertThat(result.filedAt()).isEqualTo(effectiveDate);
        assertThat(result.assessedAt()).isEqualTo(effectiveDate);
    }

    @Test
    void updateStatus_one_step_back_assessed_to_filed_clears_assessedAt_but_keeps_filedAt() {
        LocalDate filedAt = LocalDate.of(2026, 4, 1);
        TaxYear assessed = incompleteYear(TaxYearStatus.ASSESSED).toBuilder()
                .filedAt(filedAt)
                .assessedAt(LocalDate.of(2026, 9, 1))
                .build();
        given(taxYearRepository.findByUserIdAndTaxYear(USER_ID, YEAR)).willReturn(Optional.of(assessed));
        stubSaveReturnsArgument();
        stubEmptyPaymentsAndDeadlines();

        TaxYearDetailResponse result = service.updateStatus(
                YEAR, new TaxYearStatusRequest(TaxYearStatus.FILED, null), USER_ID);

        assertThat(result.status()).isEqualTo(TaxYearStatus.FILED);
        assertThat(result.assessedAt()).isNull();
        assertThat(result.filedAt()).isEqualTo(filedAt);
    }

    @Test
    void updateStatus_two_steps_back_paid_to_open_throws_IllegalStateException() {
        given(taxYearRepository.findByUserIdAndTaxYear(USER_ID, YEAR))
                .willReturn(Optional.of(incompleteYear(TaxYearStatus.PAID)));

        assertThatThrownBy(() -> service.updateStatus(
                YEAR, new TaxYearStatusRequest(TaxYearStatus.OPEN, null), USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PAID")
                .hasMessageContaining("OPEN");
        then(taxYearRepository).should(never()).save(any());
    }

    @Test
    void updateStatus_same_status_transition_throws_IllegalStateException() {
        given(taxYearRepository.findByUserIdAndTaxYear(USER_ID, YEAR))
                .willReturn(Optional.of(incompleteYear(TaxYearStatus.FILED)));

        assertThatThrownBy(() -> service.updateStatus(
                YEAR, new TaxYearStatusRequest(TaxYearStatus.FILED, null), USER_ID))
                .isInstanceOf(IllegalStateException.class);
        then(taxYearRepository).should(never()).save(any());
    }

    // =========================================================================
    // expectedTax — source selection
    // =========================================================================

    @Test
    void expectedTax_is_null_and_calculation_skipped_when_inputs_incomplete() {
        given(taxYearRepository.findByUserIdAndTaxYear(USER_ID, YEAR))
                .willReturn(Optional.of(incompleteYear(TaxYearStatus.OPEN)));
        stubEmptyPaymentsAndDeadlines();

        TaxYearDetailResponse result = service.getByYear(YEAR, USER_ID);

        assertThat(result.calculation()).isNull();
        assertThat(result.expectedTax()).isNull();
        then(taxCalculationService).shouldHaveNoInteractions();
    }

    @Test
    void expectedTax_equals_calculation_grand_total_when_inputs_complete() {
        given(taxYearRepository.findByUserIdAndTaxYear(USER_ID, YEAR))
                .willReturn(Optional.of(completeYear(TaxYearStatus.OPEN)));
        given(taxCalculationService.calculate(any(TaxInputRequest.class)))
                .willReturn(cannedResult("12345.67"));
        stubEmptyPaymentsAndDeadlines();

        TaxYearDetailResponse result = service.getByYear(YEAR, USER_ID);

        assertThat(result.expectedTax()).isEqualByComparingTo("12345.67");
    }

    @Test
    void expectedTax_equals_assessed_amount_when_status_assessed_and_amount_set() {
        TaxYear assessed = completeYear(TaxYearStatus.ASSESSED).toBuilder()
                .assessedAmount(new BigDecimal("9999.99"))
                .build();
        given(taxYearRepository.findByUserIdAndTaxYear(USER_ID, YEAR)).willReturn(Optional.of(assessed));
        given(taxCalculationService.calculate(any(TaxInputRequest.class)))
                .willReturn(cannedResult("12345.67"));
        stubEmptyPaymentsAndDeadlines();

        TaxYearDetailResponse result = service.getByYear(YEAR, USER_ID);

        // The official assessment overrides the estimated calculation
        assertThat(result.expectedTax()).isEqualByComparingTo("9999.99");
    }

    @Test
    void expectedTax_equals_assessed_amount_when_status_paid_and_amount_set() {
        TaxYear paid = completeYear(TaxYearStatus.PAID).toBuilder()
                .assessedAmount(new BigDecimal("8888.88"))
                .build();
        given(taxYearRepository.findByUserIdAndTaxYear(USER_ID, YEAR)).willReturn(Optional.of(paid));
        given(taxCalculationService.calculate(any(TaxInputRequest.class)))
                .willReturn(cannedResult("12345.67"));
        stubEmptyPaymentsAndDeadlines();

        TaxYearDetailResponse result = service.getByYear(YEAR, USER_ID);

        assertThat(result.expectedTax()).isEqualByComparingTo("8888.88");
    }

    // =========================================================================
    // openAmount arithmetic
    // =========================================================================

    @Test
    void openAmount_is_negative_when_payments_exceed_expected_tax() {
        given(taxYearRepository.findByUserIdAndTaxYear(USER_ID, YEAR))
                .willReturn(Optional.of(completeYear(TaxYearStatus.OPEN)));
        given(taxCalculationService.calculate(any(TaxInputRequest.class)))
                .willReturn(cannedResult("1000.00"));
        given(taxPaymentRepository.findByTaxYearIdAndUserIdOrderByPaymentDateAsc(YEAR_ID, USER_ID))
                .willReturn(List.of(payment(new BigDecimal("600.00")), payment(new BigDecimal("700.00"))));
        given(taxDeadlineRepository.findByTaxYearIdAndUserIdOrderByDueDateAsc(YEAR_ID, USER_ID))
                .willReturn(List.of());

        TaxYearDetailResponse result = service.getByYear(YEAR, USER_ID);

        // 1 000 expected − 1 300 paid = −300 → refund due, must NOT be clamped to 0
        assertThat(result.paidTotal()).isEqualByComparingTo("1300.00");
        assertThat(result.openAmount()).isEqualByComparingTo("-300.00");
    }

    @Test
    void openAmount_is_null_when_expected_tax_is_null_even_with_payments() {
        given(taxYearRepository.findByUserIdAndTaxYear(USER_ID, YEAR))
                .willReturn(Optional.of(incompleteYear(TaxYearStatus.OPEN)));
        given(taxPaymentRepository.findByTaxYearIdAndUserIdOrderByPaymentDateAsc(YEAR_ID, USER_ID))
                .willReturn(List.of(payment(new BigDecimal("500.00"))));
        given(taxDeadlineRepository.findByTaxYearIdAndUserIdOrderByDueDateAsc(YEAR_ID, USER_ID))
                .willReturn(List.of());

        TaxYearDetailResponse result = service.getByYear(YEAR, USER_ID);

        assertThat(result.paidTotal()).isEqualByComparingTo("500.00");
        assertThat(result.openAmount()).isNull();
    }

    // =========================================================================
    // Payment ownership
    // =========================================================================

    @Test
    void addPayment_throws_ResourceNotFoundException_when_tax_year_absent() {
        given(taxYearRepository.findByUserIdAndTaxYear(USER_ID, YEAR)).willReturn(Optional.empty());
        TaxPaymentRequest request =
                new TaxPaymentRequest(LocalDate.of(2025, 6, 30), new BigDecimal("500"), "Provisional");

        assertThatThrownBy(() -> service.addPayment(YEAR, request, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tax year");
        then(taxPaymentRepository).should(never()).save(any());
    }

    @Test
    void updatePayment_throws_ResourceNotFoundException_when_payment_belongs_to_other_user() {
        UUID foreignPaymentId = UUID.randomUUID();
        given(taxYearRepository.findByUserIdAndTaxYear(USER_ID, YEAR))
                .willReturn(Optional.of(incompleteYear(TaxYearStatus.OPEN)));
        // findByIdAndUserId is scoped by userId — a foreign payment resolves to empty
        given(taxPaymentRepository.findByIdAndUserId(foreignPaymentId, USER_ID))
                .willReturn(Optional.empty());
        TaxPaymentRequest request =
                new TaxPaymentRequest(LocalDate.of(2025, 6, 30), new BigDecimal("500"), null);

        assertThatThrownBy(() -> service.updatePayment(YEAR, foreignPaymentId, request, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tax payment");
        then(taxPaymentRepository).should(never()).save(any());
    }

    @Test
    void deletePayment_throws_when_payment_belongs_to_a_different_tax_year_of_same_user() {
        // The payment exists and belongs to the user, but hangs under ANOTHER
        // tax year — the taxYearId filter in loadPayment() must reject it.
        TaxPayment paymentOfOtherYear = payment(new BigDecimal("500.00")).toBuilder()
                .taxYearId(UUID.randomUUID())
                .build();
        given(taxYearRepository.findByUserIdAndTaxYear(USER_ID, YEAR))
                .willReturn(Optional.of(incompleteYear(TaxYearStatus.OPEN)));
        given(taxPaymentRepository.findByIdAndUserId(paymentOfOtherYear.getId(), USER_ID))
                .willReturn(Optional.of(paymentOfOtherYear));

        assertThatThrownBy(() -> service.deletePayment(YEAR, paymentOfOtherYear.getId(), USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tax payment");
        then(taxPaymentRepository).should(never()).delete(any(TaxPayment.class));
    }
}
