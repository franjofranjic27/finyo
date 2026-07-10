package ch.finyo.tax;

import ch.finyo.common.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.anyString;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;

/**
 * Pure unit tests for TaxScenarioService.
 *
 * No Spring context is started. All repositories and the TaxCalculationService
 * are mocked; the calculation result is a canned TaxResultResponse so these
 * tests only verify the ORCHESTRATION logic of TaxScenarioService:
 *
 *   1. Create semantics — every POST is a new immutable snapshot carrying the
 *      caller's userId; the tax_year row is created lazily when absent; a
 *      second default for the same year is rejected with IllegalStateException
 *      (mapped to 409) BEFORE anything is written.
 *   2. Default switching — the previous default is cleared (saveAndFlush)
 *      strictly BEFORE the new default is saved, otherwise the partial unique
 *      index ux_tax_scenario_default_per_year would be violated; setting the
 *      current default again is a no-op.
 *   3. Ownership — scenarios of another user or hanging under another tax
 *      year surface as ResourceNotFoundException (mapped to 404), never as
 *      writes or deletes.
 *   4. Listing — repository order (newest first) is preserved, an absent year
 *      yields an empty list, and the embedded calculation is only computed
 *      when the snapshot's inputs are complete.
 */
@ExtendWith(MockitoExtension.class)
class TaxScenarioServiceTest {

    private static final String USER_ID = "user-1";
    private static final int YEAR = 2025;
    private static final UUID YEAR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SCENARIO_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private TaxScenarioRepository taxScenarioRepository;

    @Mock
    private TaxYearRepository taxYearRepository;

    @Mock
    private TaxCalculationService taxCalculationService;

    @InjectMocks
    private TaxScenarioService service;

    // -------------------------------------------------------------------------
    // Builders / canned data
    // -------------------------------------------------------------------------

    private TaxYear year() {
        return TaxYear.builder()
                .id(YEAR_ID)
                .userId(USER_ID)
                .year(YEAR)
                .status(TaxYearStatus.OPEN)
                .build();
    }

    /** A scenario missing cantonCode/civilStatus/income — recompute() must yield null. */
    private TaxScenario incompleteScenario(UUID id, String name, boolean isDefault) {
        return TaxScenario.builder()
                .id(id)
                .userId(USER_ID)
                .taxYearId(YEAR_ID)
                .name(name)
                .isDefault(isDefault)
                .build();
    }

    /** A scenario whose inputs are complete enough for recompute() to run. */
    private TaxScenario completeScenario(UUID id, String name) {
        return incompleteScenario(id, name, false).toBuilder()
                .cantonCode("SG")
                .civilStatus(TaxCivilStatus.SINGLE)
                .grossEmploymentIncome(new BigDecimal("100000"))
                .build();
    }

    private TaxScenarioRequest completeRequest(String name, boolean isDefault) {
        return new TaxScenarioRequest(name, isDefault, "SG", 3203, TaxCivilStatus.SINGLE, 0,
                ChurchAffiliation.NONE, new BigDecimal("100000"), null, null, null,
                null, null, null, null, new BigDecimal("7000"), null);
    }

    /** A request without canton/civil status/income — the calculation must be skipped. */
    private TaxScenarioRequest incompleteRequest(String name, boolean isDefault) {
        return new TaxScenarioRequest(name, isDefault, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null);
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

    // -------------------------------------------------------------------------
    // Common stubs
    // -------------------------------------------------------------------------

    private void stubScenarioSaveAssignsId() {
        given(taxScenarioRepository.saveAndFlush(any(TaxScenario.class))).willAnswer(inv -> {
            TaxScenario arg = inv.getArgument(0);
            return arg.getId() != null ? arg : arg.toBuilder().id(SCENARIO_ID).build();
        });
    }

    // =========================================================================
    // create()
    // =========================================================================

    @Test
    void create_saves_new_scenario_with_userId_and_returns_calculation() {
        given(taxYearRepository.findByUserIdAndYear(USER_ID, YEAR)).willReturn(Optional.of(year()));
        stubScenarioSaveAssignsId();
        given(taxCalculationService.calculate(any(TaxInputRequest.class)))
                .willReturn(cannedResult("12345.67"));

        TaxScenarioResponse result = service.create(YEAR, completeRequest("Base", false), USER_ID);

        assertThat(result.taxYear()).isEqualTo(YEAR);
        assertThat(result.name()).isEqualTo("Base");
        assertThat(result.isDefault()).isFalse();
        assertThat(result.calculation().grandTotal()).isEqualByComparingTo("12345.67");
        then(taxScenarioRepository).should().saveAndFlush(argThat(saved ->
                USER_ID.equals(saved.getUserId())
                        && YEAR_ID.equals(saved.getTaxYearId())
                        && "Base".equals(saved.getName())
                        && !saved.isDefault()));
        // The year already existed — no lazy creation may happen
        then(taxYearRepository).should(never()).save(any());
    }

    @Test
    void create_recomputes_with_the_scenarios_own_inputs_and_the_requested_year() {
        given(taxYearRepository.findByUserIdAndYear(USER_ID, YEAR)).willReturn(Optional.of(year()));
        stubScenarioSaveAssignsId();
        given(taxCalculationService.calculate(any(TaxInputRequest.class)))
                .willReturn(cannedResult("12345.67"));

        service.create(YEAR, completeRequest("Base", false), USER_ID);

        then(taxCalculationService).should().calculate(argThat(input ->
                input.taxYear() == YEAR
                        && "SG".equals(input.cantonCode())
                        && input.civilStatus() == TaxCivilStatus.SINGLE
                        && new BigDecimal("100000").compareTo(input.grossEmploymentIncome()) == 0));
    }

    @Test
    void create_lazily_creates_the_tax_year_when_absent() {
        given(taxYearRepository.findByUserIdAndYear(USER_ID, YEAR)).willReturn(Optional.empty());
        given(taxYearRepository.save(any(TaxYear.class)))
                .willAnswer(inv -> inv.<TaxYear>getArgument(0).toBuilder().id(YEAR_ID).build());
        stubScenarioSaveAssignsId();

        TaxScenarioResponse result = service.create(YEAR, incompleteRequest("Draft", false), USER_ID);

        assertThat(result.taxYear()).isEqualTo(YEAR);
        // The minimal year must belong to the caller and start in OPEN
        then(taxYearRepository).should().save(argThat(savedYear ->
                USER_ID.equals(savedYear.getUserId())
                        && savedYear.getYear() == YEAR
                        && savedYear.getStatus() == TaxYearStatus.OPEN));
        // The scenario must hang under the freshly created year
        then(taxScenarioRepository).should().saveAndFlush(argThat(saved ->
                YEAR_ID.equals(saved.getTaxYearId()) && USER_ID.equals(saved.getUserId())));
    }

    @Test
    void create_throws_IllegalStateException_when_year_already_has_a_default() {
        given(taxYearRepository.findByUserIdAndYear(USER_ID, YEAR)).willReturn(Optional.of(year()));
        given(taxScenarioRepository.existsByTaxYearIdAndUserIdAndIsDefaultTrue(YEAR_ID, USER_ID))
                .willReturn(true);
        TaxScenarioRequest request = completeRequest("Second default", true);

        assertThatThrownBy(() -> service.create(YEAR, request, USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2025")
                .hasMessageContaining("already has a default scenario");
        then(taxScenarioRepository).should(never()).saveAndFlush(any());
    }

    @Test
    void create_with_isDefault_false_succeeds_even_when_a_default_exists() {
        given(taxYearRepository.findByUserIdAndYear(USER_ID, YEAR)).willReturn(Optional.of(year()));
        stubScenarioSaveAssignsId();

        TaxScenarioResponse result = service.create(YEAR, incompleteRequest("Non-default", false), USER_ID);

        assertThat(result.isDefault()).isFalse();
        // A non-default snapshot never needs the uniqueness guard — the check
        // must be short-circuited so an existing default cannot block it
        then(taxScenarioRepository).should(never())
                .existsByTaxYearIdAndUserIdAndIsDefaultTrue(any(), anyString());
        then(taxScenarioRepository).should().saveAndFlush(argThat(saved -> !saved.isDefault()));
    }

    @Test
    void create_returns_null_calculation_when_inputs_incomplete() {
        given(taxYearRepository.findByUserIdAndYear(USER_ID, YEAR)).willReturn(Optional.of(year()));
        stubScenarioSaveAssignsId();

        TaxScenarioResponse result = service.create(YEAR, incompleteRequest("Draft", false), USER_ID);

        assertThat(result.calculation()).isNull();
        then(taxCalculationService).shouldHaveNoInteractions();
    }

    // =========================================================================
    // setDefault()
    // =========================================================================

    @Test
    void setDefault_clears_the_previous_default_before_setting_the_new_one() {
        UUID oldDefaultId = UUID.randomUUID();
        TaxScenario target = incompleteScenario(SCENARIO_ID, "New default", false);
        TaxScenario oldDefault = incompleteScenario(oldDefaultId, "Old default", true);
        given(taxYearRepository.findByUserIdAndYear(USER_ID, YEAR)).willReturn(Optional.of(year()));
        given(taxScenarioRepository.findByIdAndUserId(SCENARIO_ID, USER_ID)).willReturn(Optional.of(target));
        given(taxScenarioRepository.findByTaxYearIdAndUserIdAndIsDefaultTrue(YEAR_ID, USER_ID))
                .willReturn(Optional.of(oldDefault));
        given(taxScenarioRepository.saveAndFlush(any(TaxScenario.class))).willAnswer(inv -> inv.getArgument(0));
        given(taxScenarioRepository.save(any(TaxScenario.class))).willAnswer(inv -> inv.getArgument(0));

        TaxScenarioResponse result = service.setDefault(YEAR, SCENARIO_ID, USER_ID);

        assertThat(result.isDefault()).isTrue();
        // Order matters: the cleared flag must hit the DB (saveAndFlush) BEFORE
        // the new default is saved, or the partial unique index would fire
        InOrder order = inOrder(taxScenarioRepository);
        then(taxScenarioRepository).should(order).saveAndFlush(argThat(cleared ->
                oldDefaultId.equals(cleared.getId()) && !cleared.isDefault()));
        then(taxScenarioRepository).should(order).save(argThat(saved ->
                SCENARIO_ID.equals(saved.getId()) && saved.isDefault()));
    }

    @Test
    void setDefault_only_saves_the_target_when_no_previous_default_exists() {
        TaxScenario target = incompleteScenario(SCENARIO_ID, "First default", false);
        given(taxYearRepository.findByUserIdAndYear(USER_ID, YEAR)).willReturn(Optional.of(year()));
        given(taxScenarioRepository.findByIdAndUserId(SCENARIO_ID, USER_ID)).willReturn(Optional.of(target));
        given(taxScenarioRepository.findByTaxYearIdAndUserIdAndIsDefaultTrue(YEAR_ID, USER_ID))
                .willReturn(Optional.empty());
        given(taxScenarioRepository.save(any(TaxScenario.class))).willAnswer(inv -> inv.getArgument(0));

        TaxScenarioResponse result = service.setDefault(YEAR, SCENARIO_ID, USER_ID);

        assertThat(result.isDefault()).isTrue();
        then(taxScenarioRepository).should(never()).saveAndFlush(any());
    }

    @Test
    void setDefault_is_idempotent_when_scenario_is_already_the_default() {
        TaxScenario target = incompleteScenario(SCENARIO_ID, "Already default", true);
        given(taxYearRepository.findByUserIdAndYear(USER_ID, YEAR)).willReturn(Optional.of(year()));
        given(taxScenarioRepository.findByIdAndUserId(SCENARIO_ID, USER_ID)).willReturn(Optional.of(target));

        TaxScenarioResponse result = service.setDefault(YEAR, SCENARIO_ID, USER_ID);

        assertThat(result.isDefault()).isTrue();
        assertThat(result.id()).isEqualTo(SCENARIO_ID);
        // A no-op must not touch the database at all
        then(taxScenarioRepository).should(never()).save(any());
        then(taxScenarioRepository).should(never()).saveAndFlush(any());
    }

    @Test
    void setDefault_throws_ResourceNotFoundException_when_year_absent() {
        given(taxYearRepository.findByUserIdAndYear(USER_ID, YEAR)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.setDefault(YEAR, SCENARIO_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tax year");
        then(taxScenarioRepository).shouldHaveNoInteractions();
    }

    @Test
    void setDefault_throws_ResourceNotFoundException_when_scenario_belongs_to_other_user() {
        given(taxYearRepository.findByUserIdAndYear(USER_ID, YEAR)).willReturn(Optional.of(year()));
        // findByIdAndUserId is scoped by userId — a foreign scenario resolves to empty
        given(taxScenarioRepository.findByIdAndUserId(SCENARIO_ID, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.setDefault(YEAR, SCENARIO_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tax scenario");
        then(taxScenarioRepository).should(never()).save(any());
        then(taxScenarioRepository).should(never()).saveAndFlush(any());
    }

    @Test
    void setDefault_throws_when_scenario_belongs_to_a_different_tax_year_of_same_user() {
        // The scenario exists and belongs to the user, but hangs under ANOTHER
        // tax year — the taxYearId filter in loadScenario() must reject it.
        TaxScenario scenarioOfOtherYear = incompleteScenario(SCENARIO_ID, "Other year", false)
                .toBuilder().taxYearId(UUID.randomUUID()).build();
        given(taxYearRepository.findByUserIdAndYear(USER_ID, YEAR)).willReturn(Optional.of(year()));
        given(taxScenarioRepository.findByIdAndUserId(SCENARIO_ID, USER_ID))
                .willReturn(Optional.of(scenarioOfOtherYear));

        assertThatThrownBy(() -> service.setDefault(YEAR, SCENARIO_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tax scenario");
        then(taxScenarioRepository).should(never()).save(any());
        then(taxScenarioRepository).should(never()).saveAndFlush(any());
    }

    // =========================================================================
    // delete()
    // =========================================================================

    @Test
    void delete_removes_an_owned_scenario() {
        TaxScenario target = incompleteScenario(SCENARIO_ID, "To delete", true);
        given(taxYearRepository.findByUserIdAndYear(USER_ID, YEAR)).willReturn(Optional.of(year()));
        given(taxScenarioRepository.findByIdAndUserId(SCENARIO_ID, USER_ID)).willReturn(Optional.of(target));

        service.delete(YEAR, SCENARIO_ID, USER_ID);

        then(taxScenarioRepository).should().delete(target);
    }

    @Test
    void delete_throws_ResourceNotFoundException_for_a_foreign_scenario() {
        given(taxYearRepository.findByUserIdAndYear(USER_ID, YEAR)).willReturn(Optional.of(year()));
        given(taxScenarioRepository.findByIdAndUserId(SCENARIO_ID, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(YEAR, SCENARIO_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tax scenario");
        then(taxScenarioRepository).should(never()).delete(any(TaxScenario.class));
    }

    // =========================================================================
    // list()
    // =========================================================================

    @Test
    void list_maps_scenarios_to_responses_preserving_newest_first_order() {
        TaxScenario newer = incompleteScenario(UUID.randomUUID(), "Newer", true);
        TaxScenario older = incompleteScenario(UUID.randomUUID(), "Older", false);
        given(taxYearRepository.findByUserIdAndYear(USER_ID, YEAR)).willReturn(Optional.of(year()));
        given(taxScenarioRepository.findByTaxYearIdAndUserIdOrderByCreatedAtDesc(YEAR_ID, USER_ID))
                .willReturn(List.of(newer, older));

        List<TaxScenarioResponse> result = service.list(YEAR, USER_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Newer");
        assertThat(result.get(0).isDefault()).isTrue();
        assertThat(result.get(0).id()).isEqualTo(newer.getId());
        assertThat(result.get(1).name()).isEqualTo("Older");
        assertThat(result.get(1).isDefault()).isFalse();
        assertThat(result).allSatisfy(response -> assertThat(response.taxYear()).isEqualTo(YEAR));
    }

    @Test
    void list_returns_empty_list_when_year_absent() {
        given(taxYearRepository.findByUserIdAndYear(USER_ID, YEAR)).willReturn(Optional.empty());

        // An unknown year is a valid empty state, NOT a 404
        assertThat(service.list(YEAR, USER_ID)).isEmpty();
        then(taxScenarioRepository).shouldHaveNoInteractions();
    }

    @Test
    void list_includes_calculation_only_for_scenarios_with_complete_inputs() {
        TaxScenario complete = completeScenario(UUID.randomUUID(), "Complete");
        TaxScenario incomplete = incompleteScenario(UUID.randomUUID(), "Incomplete", false);
        given(taxYearRepository.findByUserIdAndYear(USER_ID, YEAR)).willReturn(Optional.of(year()));
        given(taxScenarioRepository.findByTaxYearIdAndUserIdOrderByCreatedAtDesc(YEAR_ID, USER_ID))
                .willReturn(List.of(complete, incomplete));
        given(taxCalculationService.calculate(any(TaxInputRequest.class)))
                .willReturn(cannedResult("12345.67"));

        List<TaxScenarioResponse> result = service.list(YEAR, USER_ID);

        assertThat(result.get(0).calculation().grandTotal()).isEqualByComparingTo("12345.67");
        assertThat(result.get(1).calculation()).isNull();
        then(taxCalculationService).should().calculate(argThat(input -> input.taxYear() == YEAR));
    }
}
