package ch.finyo.insurance;

import ch.finyo.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InsuranceService {

    private final InsuranceTypeRepository insuranceTypeRepository;
    private final UserInsuranceStatusRepository userInsuranceStatusRepository;

    public List<InsuranceOverviewResponse> getOverview(String userId) {
        List<InsuranceType> types = insuranceTypeRepository.findAllByOrderBySortOrderAsc();
        Map<UUID, UserInsuranceStatus> statusMap = userInsuranceStatusRepository.findByUserId(userId)
            .stream().collect(Collectors.toMap(s -> s.getInsuranceType().getId(), s -> s));

        return types.stream().map(type -> {
            UserInsuranceStatus status = statusMap.get(type.getId());
            return new InsuranceOverviewResponse(
                type.getId(), type.getCode(), type.getNameEn(), type.getNameDe(),
                type.getDescriptionEn(), type.getDescriptionDe(),
                type.isMandatory(), type.isRecommended(),
                type.getTypicalCostMin(), type.getTypicalCostMax(), type.getComparisonUrl(), type.getSortOrder(),
                status != null && status.isHasInsurance(),
                status != null ? status.getUpdatedAt() : null
            );
        }).toList();
    }

    @Transactional
    public InsuranceOverviewResponse updateStatus(UUID insuranceTypeId, boolean hasInsurance, String userId) {
        InsuranceType type = insuranceTypeRepository.findById(insuranceTypeId)
            .orElseThrow(() -> ResourceNotFoundException.of("InsuranceType", insuranceTypeId));

        Optional<UserInsuranceStatus> existing = userInsuranceStatusRepository
            .findByUserIdAndInsuranceTypeId(userId, insuranceTypeId);

        UserInsuranceStatus status;
        if (existing.isPresent()) {
            status = existing.get();
            status = UserInsuranceStatus.builder()
                .id(status.getId())
                .userId(userId)
                .insuranceType(type)
                .hasInsurance(hasInsurance)
                .updatedAt(OffsetDateTime.now())
                .build();
        } else {
            status = UserInsuranceStatus.builder()
                .userId(userId)
                .insuranceType(type)
                .hasInsurance(hasInsurance)
                .updatedAt(OffsetDateTime.now())
                .build();
        }
        userInsuranceStatusRepository.save(status);

        return new InsuranceOverviewResponse(
            type.getId(), type.getCode(), type.getNameEn(), type.getNameDe(),
            type.getDescriptionEn(), type.getDescriptionDe(),
            type.isMandatory(), type.isRecommended(),
            type.getTypicalCostMin(), type.getTypicalCostMax(), type.getComparisonUrl(), type.getSortOrder(),
            hasInsurance, OffsetDateTime.now()
        );
    }
}
