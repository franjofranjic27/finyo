package ch.finyo.budget;

import ch.finyo.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FixedCostService {

    private static final String RESOURCE_NAME = "FixedCost";

    private static final Comparator<FixedCostResponse> BY_YEARLY_AMOUNT_DESC_THEN_NAME =
            Comparator.comparing(FixedCostResponse::amountPerYear, Comparator.reverseOrder())
                    .thenComparing(FixedCostResponse::name);

    private final FixedCostRepository fixedCostRepository;

    @Transactional(readOnly = true)
    public FixedCostListResponse getAll(String userId) {
        log.debug("Fetching fixed costs for user={}", userId);
        return FixedCostListResponse.of(mapSorted(fixedCostRepository.findByUserId(userId)));
    }

    /** Monthly total across all fixed costs of the user (2 dp, HALF_UP per item). */
    @Transactional(readOnly = true)
    public BigDecimal getTotalPerMonth(String userId) {
        return FixedCostListResponse.of(mapSorted(fixedCostRepository.findByUserId(userId))).totalPerMonth();
    }

    @Transactional
    public FixedCostResponse create(FixedCostRequest request, String userId) {
        log.info("Creating fixed cost name='{}' for user={}", request.name(), userId);
        var fixedCost = FixedCost.builder()
                .userId(userId)
                .name(request.name())
                .category(request.category())
                .paymentInterval(request.paymentInterval())
                .amount(request.amount())
                .build();

        FixedCost saved = fixedCostRepository.save(fixedCost);
        log.info("Created fixed cost id={} for user={}", saved.getId(), userId);
        return FixedCostResponse.from(saved);
    }

    @Transactional
    public FixedCostResponse update(UUID id, FixedCostRequest request, String userId) {
        log.info("Updating fixed cost id={} for user={}", id, userId);
        var existing = fixedCostRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE_NAME, id));

        var updated = FixedCost.builder()
                .id(existing.getId())
                .userId(existing.getUserId())
                .name(request.name())
                .category(request.category())
                .paymentInterval(request.paymentInterval())
                .amount(request.amount())
                .build();

        FixedCost saved = fixedCostRepository.save(updated);
        log.info("Updated fixed cost id={} for user={}", saved.getId(), userId);
        return FixedCostResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id, String userId) {
        log.info("Deleting fixed cost id={} for user={}", id, userId);
        fixedCostRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE_NAME, id));
        fixedCostRepository.deleteById(id);
        log.info("Deleted fixed cost id={} for user={}", id, userId);
    }

    private List<FixedCostResponse> mapSorted(List<FixedCost> fixedCosts) {
        return fixedCosts.stream()
                .map(FixedCostResponse::from)
                .sorted(BY_YEARLY_AMOUNT_DESC_THEN_NAME)
                .toList();
    }
}
