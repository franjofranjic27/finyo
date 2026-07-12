package ch.finyo.pillar3;

import ch.finyo.common.ResourceNotFoundException;
import ch.finyo.tax.Pillar3CalculationService;
import ch.finyo.tax.Pillar3InputRequest;
import ch.finyo.tax.Pillar3ResultResponse;
import ch.finyo.tax.TaxCivilStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
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
import static org.mockito.Mockito.inOrder;

/**
 * Pure unit tests for Pillar3ScenarioService.
 *
 * No Spring context is started. Repositories and the Pillar3CalculationService
 * are mocked; the calculation result is a canned Pillar3ResultResponse so these
 * tests only verify the ORCHESTRATION logic of Pillar3ScenarioService:
 *
 *   1. Create semantics — every POST is a new immutable snapshot carrying the
 *      caller's userId; an unknown productId is rejected with
 *      ResourceNotFoundException (mapped to 404); a second default for the
 *      same user is rejected with IllegalStateException (mapped to 409)
 *      BEFORE anything is written, and a concurrent insert surfacing as
 *      DataIntegrityViolationException is translated to the same 409.
 *   2. Default switching — the previous default is cleared (saveAndFlush)
 *      strictly BEFORE the new default is saved, otherwise the partial unique
 *      index ux_pillar3_scenario_default_per_user would be violated; setting
 *      the current default again is a no-op.
 *   3. Effective return — a linked product overrides the stored snapshot
 *      percent with the Pillar3ReturnModel net rate; a deleted product (empty
 *      Optional) falls back to the snapshot percent.
 */
@ExtendWith(MockitoExtension.class)
class Pillar3ScenarioServiceTest {

    private static final String USER_ID = "user-1";
    private static final UUID SCENARIO_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PRODUCT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock
    private Pillar3ScenarioRepository scenarioRepository;

    @Mock
    private Pillar3ProductRepository productRepository;

    @Mock
    private Pillar3CalculationService pillar3CalculationService;

    @InjectMocks
    private Pillar3ScenarioService service;

    // -------------------------------------------------------------------------
    // Builders / canned data
    // -------------------------------------------------------------------------

    private Pillar3Scenario scenario(UUID id, String name, boolean isDefault) {
        return Pillar3Scenario.builder()
                .id(id)
                .userId(USER_ID)
                .name(name)
                .isDefault(isDefault)
                .currentBalance(new BigDecimal("10000"))
                .annualContribution(new BigDecimal("7000"))
                .assumedAnnualReturnPercent(new BigDecimal("3.50"))
                .yearsToRetirement(20)
                .build();
    }

    private Pillar3Product product(BigDecimal equityPct, BigDecimal terPct) {
        return Pillar3Product.builder()
                .id(PRODUCT_ID)
                .provider("Testbank")
                .name("Test Fund 45")
                .isin("CH0000000001")
                .equityPct(equityPct)
                .terPct(terPct)
                .active(true)
                .sortOrder(0)
                .build();
    }

    private Pillar3ScenarioRequest request(String name, boolean isDefault, UUID productId) {
        return new Pillar3ScenarioRequest(name, isDefault,
                new BigDecimal("10000"), new BigDecimal("7000"), 3.5, 20,
                new BigDecimal("100000"), TaxCivilStatus.SINGLE, "SG", 2025, productId);
    }

    private Pillar3ResultResponse cannedResult(String projectedBalance) {
        return new Pillar3ResultResponse(
                new BigDecimal("7000"), new BigDecimal("7258"), false, new BigDecimal("258"),
                new BigDecimal("1500.00"), new BigDecimal("1550.00"),
                new BigDecimal(projectedBalance), new BigDecimal("140000"), new BigDecimal("50000"),
                new BigDecimal("7000.00"), new BigDecimal("193000.00"), new BigDecimal("160000.00"),
                List.of());
    }

    // -------------------------------------------------------------------------
    // Common stubs
    // -------------------------------------------------------------------------

    private void stubScenarioSaveAssignsId() {
        given(scenarioRepository.saveAndFlush(any(Pillar3Scenario.class))).willAnswer(inv -> {
            Pillar3Scenario arg = inv.getArgument(0);
            return arg.getId() != null ? arg : arg.toBuilder().id(SCENARIO_ID).build();
        });
    }

    private void stubCannedCalculation() {
        given(pillar3CalculationService.calculate(any(Pillar3InputRequest.class)))
                .willReturn(cannedResult("200000.00"));
    }

    // =========================================================================
    // create()
    // =========================================================================

    @Test
    void create_saves_new_scenario_with_userId_and_returns_calculation() {
        stubScenarioSaveAssignsId();
        stubCannedCalculation();

        Pillar3ScenarioResponse result = service.create(request("Base", false, null), USER_ID);

        assertThat(result.name()).isEqualTo("Base");
        assertThat(result.isDefault()).isFalse();
        assertThat(result.product()).isNull();
        assertThat(result.effectiveReturnPercent()).isEqualTo(3.5);
        assertThat(result.calculation().projectedBalanceAtRetirement()).isEqualByComparingTo("200000.00");
        then(scenarioRepository).should().saveAndFlush(argThat(saved ->
                USER_ID.equals(saved.getUserId())
                        && "Base".equals(saved.getName())
                        && !saved.isDefault()
                        && new BigDecimal("3.5").compareTo(saved.getAssumedAnnualReturnPercent()) == 0));
    }

    @Test
    void create_throws_ResourceNotFoundException_for_unknown_productId() {
        given(productRepository.existsById(PRODUCT_ID)).willReturn(false);
        Pillar3ScenarioRequest request = request("With product", false, PRODUCT_ID);

        assertThatThrownBy(() -> service.create(request, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Pillar3Product");
        then(scenarioRepository).shouldHaveNoInteractions();
    }

    @Test
    void create_throws_IllegalStateException_when_user_already_has_a_default() {
        given(scenarioRepository.existsByUserIdAndIsDefaultTrue(USER_ID)).willReturn(true);
        Pillar3ScenarioRequest request = request("Second default", true, null);

        assertThatThrownBy(() -> service.create(request, USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already has a default");
        then(scenarioRepository).should(never()).saveAndFlush(any());
    }

    @Test
    void create_translates_DataIntegrityViolation_from_concurrent_default_insert_to_conflict() {
        // The racy pre-check passed, but a concurrent request won the partial
        // unique index — the constraint violation must still surface as a 409.
        given(scenarioRepository.existsByUserIdAndIsDefaultTrue(USER_ID)).willReturn(false);
        given(scenarioRepository.saveAndFlush(any(Pillar3Scenario.class)))
                .willThrow(new DataIntegrityViolationException("ux_pillar3_scenario_default_per_user"));
        Pillar3ScenarioRequest request = request("Racy default", true, null);

        assertThatThrownBy(() -> service.create(request, USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already has a default")
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }

    // =========================================================================
    // setDefault()
    // =========================================================================

    @Test
    void setDefault_clears_the_previous_default_before_setting_the_new_one() {
        UUID oldDefaultId = UUID.randomUUID();
        Pillar3Scenario target = scenario(SCENARIO_ID, "New default", false);
        Pillar3Scenario oldDefault = scenario(oldDefaultId, "Old default", true);
        given(scenarioRepository.findByIdAndUserId(SCENARIO_ID, USER_ID)).willReturn(Optional.of(target));
        given(scenarioRepository.findByUserIdAndIsDefaultTrue(USER_ID)).willReturn(Optional.of(oldDefault));
        given(scenarioRepository.saveAndFlush(any(Pillar3Scenario.class))).willAnswer(inv -> inv.getArgument(0));
        given(scenarioRepository.save(any(Pillar3Scenario.class))).willAnswer(inv -> inv.getArgument(0));
        stubCannedCalculation();

        Pillar3ScenarioResponse result = service.setDefault(SCENARIO_ID, USER_ID);

        assertThat(result.isDefault()).isTrue();
        // Order matters: the cleared flag must hit the DB (saveAndFlush) BEFORE
        // the new default is saved, or the partial unique index would fire
        InOrder order = inOrder(scenarioRepository);
        then(scenarioRepository).should(order).saveAndFlush(argThat(cleared ->
                oldDefaultId.equals(cleared.getId()) && !cleared.isDefault()));
        then(scenarioRepository).should(order).save(argThat(saved ->
                SCENARIO_ID.equals(saved.getId()) && saved.isDefault()));
    }

    @Test
    void setDefault_is_idempotent_when_scenario_is_already_the_default() {
        Pillar3Scenario target = scenario(SCENARIO_ID, "Already default", true);
        given(scenarioRepository.findByIdAndUserId(SCENARIO_ID, USER_ID)).willReturn(Optional.of(target));
        stubCannedCalculation();

        Pillar3ScenarioResponse result = service.setDefault(SCENARIO_ID, USER_ID);

        assertThat(result.isDefault()).isTrue();
        assertThat(result.id()).isEqualTo(SCENARIO_ID);
        // A no-op must not touch the database at all
        then(scenarioRepository).should(never()).save(any());
        then(scenarioRepository).should(never()).saveAndFlush(any());
    }

    @Test
    void setDefault_throws_ResourceNotFoundException_when_scenario_belongs_to_other_user() {
        // findByIdAndUserId is scoped by userId — a foreign scenario resolves to empty
        given(scenarioRepository.findByIdAndUserId(SCENARIO_ID, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.setDefault(SCENARIO_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Pillar3 scenario");
        then(scenarioRepository).should(never()).save(any());
        then(scenarioRepository).should(never()).saveAndFlush(any());
    }

    // =========================================================================
    // delete()
    // =========================================================================

    @Test
    void delete_removes_an_owned_scenario() {
        Pillar3Scenario target = scenario(SCENARIO_ID, "To delete", true);
        given(scenarioRepository.findByIdAndUserId(SCENARIO_ID, USER_ID)).willReturn(Optional.of(target));

        service.delete(SCENARIO_ID, USER_ID);

        then(scenarioRepository).should().delete(target);
    }

    @Test
    void delete_throws_ResourceNotFoundException_for_a_foreign_scenario() {
        given(scenarioRepository.findByIdAndUserId(SCENARIO_ID, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(SCENARIO_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Pillar3 scenario");
        then(scenarioRepository).should(never()).delete(any(Pillar3Scenario.class));
    }

    // =========================================================================
    // list() / effective return resolution
    // =========================================================================

    @Test
    void list_maps_scenarios_to_responses_preserving_newest_first_order() {
        Pillar3Scenario newer = scenario(UUID.randomUUID(), "Newer", true);
        Pillar3Scenario older = scenario(UUID.randomUUID(), "Older", false);
        given(scenarioRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .willReturn(List.of(newer, older));
        stubCannedCalculation();

        List<Pillar3ScenarioResponse> result = service.list(USER_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Newer");
        assertThat(result.get(0).isDefault()).isTrue();
        assertThat(result.get(1).name()).isEqualTo("Older");
        assertThat(result.get(1).isDefault()).isFalse();
    }

    @Test
    void list_uses_the_products_derived_net_rate_when_a_product_is_linked() {
        BigDecimal equityPct = new BigDecimal("45");
        BigDecimal terPct = new BigDecimal("0.40");
        double expectedNetRate = Pillar3ReturnModel.netReturnPct(equityPct, terPct).doubleValue();
        Pillar3Scenario linked = scenario(SCENARIO_ID, "Linked", false)
                .toBuilder().productId(PRODUCT_ID).build();
        given(scenarioRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).willReturn(List.of(linked));
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product(equityPct, terPct)));
        stubCannedCalculation();

        List<Pillar3ScenarioResponse> result = service.list(USER_ID);

        // 45% equity → 3.25% gross, minus 0.40 TER → 2.85% net
        assertThat(result.getFirst().effectiveReturnPercent()).isEqualTo(expectedNetRate);
        assertThat(result.getFirst().product()).isNotNull();
        assertThat(result.getFirst().product().netReturnPct()).isEqualByComparingTo("2.85");
        // The stored snapshot percent (3.5) must NOT reach the calculation
        then(pillar3CalculationService).should().calculate(argThat(input ->
                input.assumedAnnualReturnPercent() == expectedNetRate));
    }

    @Test
    void list_falls_back_to_the_stored_percent_when_the_linked_product_was_deleted() {
        Pillar3Scenario orphaned = scenario(SCENARIO_ID, "Orphaned", false)
                .toBuilder().productId(PRODUCT_ID).build();
        given(scenarioRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).willReturn(List.of(orphaned));
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());
        stubCannedCalculation();

        List<Pillar3ScenarioResponse> result = service.list(USER_ID);

        assertThat(result.getFirst().product()).isNull();
        assertThat(result.getFirst().effectiveReturnPercent()).isEqualTo(3.5);
        then(pillar3CalculationService).should().calculate(argThat(input ->
                input.assumedAnnualReturnPercent() == 3.5));
    }
}
