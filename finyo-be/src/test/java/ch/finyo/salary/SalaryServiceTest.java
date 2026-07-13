package ch.finyo.salary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

/**
 * Pure unit tests for SalaryService.
 *
 * Focus areas:
 *   1. GET without a row: Swiss default rates plus computed result, nothing persisted.
 *   2. GET with a row: stored values plus computed result.
 *   3. Upsert semantics: first PUT creates the row, later PUTs reuse its id.
 *   4. Input-mode normalization: the entered gross is stored, the counterpart derived.
 *   5. Mode-dependent required fields: YEARLY needs grossYearly, MONTHLY needs grossMonthly.
 */
@ExtendWith(MockitoExtension.class)
class SalaryServiceTest {

    private static final String USER_ID = "user-salary-1";

    @Mock
    private SalaryProfileRepository salaryProfileRepository;

    @InjectMocks
    private SalaryService salaryService;

    private SalaryProfile referenceProfile(UUID id) {
        return SalaryProfile.builder()
                .id(id)
                .userId(USER_ID)
                .inputMode(SalaryInputMode.MONTHLY)
                .grossMonthly(new BigDecimal("5787"))
                .grossYearly(new BigDecimal("69444"))
                .thirteenthSalary(false)
                .ahvPct(new BigDecimal("5.3"))
                .alvPct(new BigDecimal("1.1"))
                .nbuPct(new BigDecimal("0.455"))
                .ktgPct(new BigDecimal("0.17"))
                .pensionFixed(new BigDecimal("85.35"))
                .otherFixed(new BigDecimal("11"))
                .build();
    }

    private SalaryRequest request(SalaryInputMode inputMode, String grossMonthly, String grossYearly,
                                  Boolean thirteenthSalary) {
        return new SalaryRequest(
                inputMode,
                grossMonthly != null ? new BigDecimal(grossMonthly) : null,
                grossYearly != null ? new BigDecimal(grossYearly) : null,
                thirteenthSalary,
                new BigDecimal("5.3"), new BigDecimal("1.1"),
                new BigDecimal("0.455"), new BigDecimal("0.17"),
                new BigDecimal("85.35"), new BigDecimal("11"));
    }

    private SalaryRequest referenceRequest() {
        return request(SalaryInputMode.MONTHLY, "5787", null, false);
    }

    // =========================================================================
    // get()
    // =========================================================================

    @Test
    void get_returns_default_rates_without_persisting_when_no_row_exists() {
        given(salaryProfileRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        SalaryResponse response = salaryService.get(USER_ID);

        assertThat(response.inputMode()).isEqualTo(SalaryInputMode.MONTHLY);
        assertThat(response.grossMonthly()).isEqualByComparingTo("0");
        assertThat(response.grossYearly()).isEqualByComparingTo("0");
        assertThat(response.thirteenthSalary()).isFalse();
        assertThat(response.ahvPct()).isEqualByComparingTo("5.3");
        assertThat(response.alvPct()).isEqualByComparingTo("1.1");
        assertThat(response.nbuPct()).isEqualByComparingTo("0.455");
        assertThat(response.ktgPct()).isEqualByComparingTo("0.17");
        assertThat(response.pensionFixed()).isEqualByComparingTo("0");
        assertThat(response.otherFixed()).isEqualByComparingTo("0");
        assertThat(response.result().netMonthly()).isEqualByComparingTo("0");
        assertThat(response.result().deductions()).hasSize(6);

        then(salaryProfileRepository).should(never()).save(any());
    }

    @Test
    void get_returns_the_stored_profile_with_the_computed_result() {
        given(salaryProfileRepository.findByUserId(USER_ID))
                .willReturn(Optional.of(referenceProfile(UUID.randomUUID())));

        SalaryResponse response = salaryService.get(USER_ID);

        assertThat(response.grossMonthly()).isEqualByComparingTo("5787");
        assertThat(response.pensionFixed()).isEqualByComparingTo("85.35");
        assertThat(response.result().totalPerMonth()).isEqualByComparingTo("502.90");
        assertThat(response.result().netMonthly()).isEqualByComparingTo("5284.10");
    }

    // =========================================================================
    // upsert()
    // =========================================================================

    @Test
    void upsert_creates_a_new_row_on_first_put() {
        given(salaryProfileRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(salaryProfileRepository.save(any(SalaryProfile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        SalaryResponse response = salaryService.upsert(referenceRequest(), USER_ID);

        assertThat(response.result().netMonthly()).isEqualByComparingTo("5284.10");
        then(salaryProfileRepository).should()
                .save(argThat(p -> p.getId() == null && USER_ID.equals(p.getUserId())));
    }

    @Test
    void upsert_reuses_the_existing_row_id_on_subsequent_puts() {
        UUID existingId = UUID.randomUUID();
        given(salaryProfileRepository.findByUserId(USER_ID))
                .willReturn(Optional.of(referenceProfile(existingId)));
        given(salaryProfileRepository.save(any(SalaryProfile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        SalaryResponse response = salaryService.upsert(referenceRequest(), USER_ID);

        assertThat(response.grossMonthly()).isEqualByComparingTo("5787");
        then(salaryProfileRepository).should()
                .save(argThat(p -> existingId.equals(p.getId()) && USER_ID.equals(p.getUserId())));
    }

    @Test
    void upsert_treats_a_null_thirteenth_salary_as_false() {
        given(salaryProfileRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(salaryProfileRepository.save(any(SalaryProfile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        SalaryResponse response = salaryService.upsert(
                request(SalaryInputMode.MONTHLY, "5787", null, null), USER_ID);

        assertThat(response.thirteenthSalary()).isFalse();
        then(salaryProfileRepository).should().save(argThat(p -> !p.isThirteenthSalary()));
    }

    // =========================================================================
    // upsert() — input-mode normalization
    // =========================================================================

    @Test
    void upsert_treats_a_null_input_mode_as_monthly_and_derives_the_yearly_gross() {
        given(salaryProfileRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(salaryProfileRepository.save(any(SalaryProfile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        SalaryResponse response = salaryService.upsert(request(null, "5787", null, false), USER_ID);

        assertThat(response.inputMode()).isEqualTo(SalaryInputMode.MONTHLY);
        assertThat(response.grossMonthly()).isEqualByComparingTo("5787");
        assertThat(response.grossYearly()).isEqualByComparingTo("69444");
    }

    @Test
    void upsert_monthly_with_13th_salary_derives_the_yearly_gross_from_13_months() {
        given(salaryProfileRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(salaryProfileRepository.save(any(SalaryProfile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        SalaryResponse response = salaryService.upsert(
                request(SalaryInputMode.MONTHLY, "6000", null, true), USER_ID);

        assertThat(response.grossYearly()).isEqualByComparingTo("78000");
    }

    @Test
    void upsert_yearly_derives_the_monthly_gross_rounded_to_2_decimals() {
        given(salaryProfileRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(salaryProfileRepository.save(any(SalaryProfile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // 100000 / 13 = 7692.307... -> 7692.31 (HALF_UP)
        SalaryResponse response = salaryService.upsert(
                request(SalaryInputMode.YEARLY, null, "100000", true), USER_ID);

        assertThat(response.inputMode()).isEqualTo(SalaryInputMode.YEARLY);
        assertThat(response.grossYearly()).isEqualByComparingTo("100000");
        assertThat(response.grossMonthly()).isEqualByComparingTo("7692.31");
        then(salaryProfileRepository).should()
                .save(argThat(p -> p.getInputMode() == SalaryInputMode.YEARLY));
    }

    @Test
    void upsert_yearly_without_13th_salary_derives_the_monthly_gross_from_12_months() {
        given(salaryProfileRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(salaryProfileRepository.save(any(SalaryProfile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        SalaryResponse response = salaryService.upsert(
                request(SalaryInputMode.YEARLY, null, "90000", false), USER_ID);

        assertThat(response.grossMonthly()).isEqualByComparingTo("7500");
    }

    @Test
    void upsert_yearly_without_gross_yearly_is_rejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> salaryService.upsert(
                        request(SalaryInputMode.YEARLY, "5787", null, false), USER_ID))
                .withMessage("grossYearly is required for YEARLY input mode");

        then(salaryProfileRepository).should(never()).save(any());
    }

    @Test
    void upsert_monthly_without_gross_monthly_is_rejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> salaryService.upsert(
                        request(SalaryInputMode.MONTHLY, null, "90000", false), USER_ID))
                .withMessage("grossMonthly is required for MONTHLY input mode");

        then(salaryProfileRepository).should(never()).save(any());
    }
}
