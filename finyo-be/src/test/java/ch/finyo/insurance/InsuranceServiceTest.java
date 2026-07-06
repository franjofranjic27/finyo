package ch.finyo.insurance;

import ch.finyo.common.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
 * Pure unit tests for InsuranceService.
 *
 * Focus areas:
 *   1. getOverview() merges the static insurance catalogue with the user's
 *      per-type status; types without a status default to hasInsurance=false.
 *   2. updateStatus() upserts: it reuses the existing status row's id when
 *      present and creates a fresh row otherwise — always scoped to the user.
 *   3. Unknown insurance type → ResourceNotFoundException.
 */
@ExtendWith(MockitoExtension.class)
class InsuranceServiceTest {

    private static final String USER_ID = "user-ins-1";

    @Mock
    private InsuranceTypeRepository insuranceTypeRepository;

    @Mock
    private UserInsuranceStatusRepository userInsuranceStatusRepository;

    @InjectMocks
    private InsuranceService insuranceService;

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private InsuranceType buildType(UUID id, String code, int sortOrder) {
        return InsuranceType.builder()
                .id(id)
                .code(code)
                .nameEn(code + " EN")
                .nameDe(code + " DE")
                .mandatory("HEALTH_BASIC".equals(code))
                .recommended(true)
                .typicalCostMin(new BigDecimal("100"))
                .typicalCostMax(new BigDecimal("500"))
                .comparisonUrl("https://example.org/" + code)
                .sortOrder(sortOrder)
                .build();
    }

    private UserInsuranceStatus buildStatus(InsuranceType type, boolean hasInsurance) {
        return UserInsuranceStatus.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .insuranceType(type)
                .hasInsurance(hasInsurance)
                .updatedAt(OffsetDateTime.parse("2026-06-01T08:00:00Z"))
                .build();
    }

    // =========================================================================
    // getOverview()
    // =========================================================================

    @Test
    void getOverview_merges_catalogue_with_user_status() {
        UUID coveredTypeId = UUID.randomUUID();
        UUID uncoveredTypeId = UUID.randomUUID();
        InsuranceType covered = buildType(coveredTypeId, "HEALTH_BASIC", 1);
        InsuranceType uncovered = buildType(uncoveredTypeId, "LIABILITY", 2);
        given(insuranceTypeRepository.findAllByOrderBySortOrderAsc())
                .willReturn(List.of(covered, uncovered));
        given(userInsuranceStatusRepository.findByUserId(USER_ID))
                .willReturn(List.of(buildStatus(covered, true)));

        List<InsuranceOverviewResponse> result = insuranceService.getOverview(USER_ID);

        assertThat(result).hasSize(2);
        InsuranceOverviewResponse first = result.get(0);
        assertThat(first.code()).isEqualTo("HEALTH_BASIC");
        assertThat(first.hasInsurance()).isTrue();
        assertThat(first.statusUpdatedAt()).isEqualTo(OffsetDateTime.parse("2026-06-01T08:00:00Z"));
        assertThat(first.mandatory()).isTrue();
        InsuranceOverviewResponse second = result.get(1);
        assertThat(second.hasInsurance()).isFalse();
        assertThat(second.statusUpdatedAt()).isNull();
    }

    @Test
    void getOverview_carries_all_catalogue_fields_into_the_response() {
        UUID typeId = UUID.randomUUID();
        given(insuranceTypeRepository.findAllByOrderBySortOrderAsc())
                .willReturn(List.of(buildType(typeId, "HOUSEHOLD", 3)));
        given(userInsuranceStatusRepository.findByUserId(USER_ID)).willReturn(List.of());

        InsuranceOverviewResponse response = insuranceService.getOverview(USER_ID).get(0);

        assertThat(response.id()).isEqualTo(typeId);
        assertThat(response.nameEn()).isEqualTo("HOUSEHOLD EN");
        assertThat(response.nameDe()).isEqualTo("HOUSEHOLD DE");
        assertThat(response.typicalCostMin()).isEqualByComparingTo("100");
        assertThat(response.typicalCostMax()).isEqualByComparingTo("500");
        assertThat(response.comparisonUrl()).isEqualTo("https://example.org/HOUSEHOLD");
        assertThat(response.sortOrder()).isEqualTo(3);
    }

    @Test
    void getOverview_returns_empty_list_when_the_catalogue_is_empty() {
        given(insuranceTypeRepository.findAllByOrderBySortOrderAsc()).willReturn(List.of());
        given(userInsuranceStatusRepository.findByUserId(USER_ID)).willReturn(List.of());

        assertThat(insuranceService.getOverview(USER_ID)).isEmpty();
    }

    @Test
    void getOverview_ignores_statuses_of_types_not_in_the_catalogue() {
        // A stale status row referencing a removed type must not break the overview
        InsuranceType removedType = buildType(UUID.randomUUID(), "LEGACY", 9);
        InsuranceType activeType = buildType(UUID.randomUUID(), "LIABILITY", 1);
        given(insuranceTypeRepository.findAllByOrderBySortOrderAsc()).willReturn(List.of(activeType));
        given(userInsuranceStatusRepository.findByUserId(USER_ID))
                .willReturn(List.of(buildStatus(removedType, true)));

        List<InsuranceOverviewResponse> result = insuranceService.getOverview(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).hasInsurance()).isFalse();
    }

    // =========================================================================
    // updateStatus()
    // =========================================================================

    @Test
    void updateStatus_creates_a_new_status_row_when_none_exists() {
        UUID typeId = UUID.randomUUID();
        InsuranceType type = buildType(typeId, "LIABILITY", 1);
        given(insuranceTypeRepository.findById(typeId)).willReturn(Optional.of(type));
        given(userInsuranceStatusRepository.findByUserIdAndInsuranceTypeId(USER_ID, typeId))
                .willReturn(Optional.empty());

        InsuranceOverviewResponse result = insuranceService.updateStatus(typeId, true, USER_ID);

        assertThat(result.hasInsurance()).isTrue();
        assertThat(result.code()).isEqualTo("LIABILITY");
        then(userInsuranceStatusRepository).should().save(argThat(s ->
                s.getId() == null
                        && USER_ID.equals(s.getUserId())
                        && s.isHasInsurance()
                        && typeId.equals(s.getInsuranceType().getId())));
    }

    @Test
    void updateStatus_reuses_the_existing_row_id_when_a_status_already_exists() {
        UUID typeId = UUID.randomUUID();
        InsuranceType type = buildType(typeId, "LIABILITY", 1);
        UserInsuranceStatus existing = buildStatus(type, true);
        UUID existingRowId = existing.getId();
        given(insuranceTypeRepository.findById(typeId)).willReturn(Optional.of(type));
        given(userInsuranceStatusRepository.findByUserIdAndInsuranceTypeId(USER_ID, typeId))
                .willReturn(Optional.of(existing));

        InsuranceOverviewResponse result = insuranceService.updateStatus(typeId, false, USER_ID);

        assertThat(result.hasInsurance()).isFalse();
        then(userInsuranceStatusRepository).should().save(argThat(s ->
                existingRowId.equals(s.getId()) && !s.isHasInsurance()));
    }

    @Test
    void updateStatus_throws_ResourceNotFoundException_for_an_unknown_insurance_type() {
        UUID unknownTypeId = UUID.randomUUID();
        given(insuranceTypeRepository.findById(unknownTypeId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> insuranceService.updateStatus(unknownTypeId, true, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("InsuranceType");
        then(userInsuranceStatusRepository).should(never()).save(any());
    }
}
