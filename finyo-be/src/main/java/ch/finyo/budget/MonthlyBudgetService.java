package ch.finyo.budget;

import ch.finyo.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyBudgetService {

    private static final String POSITION_RESOURCE_NAME = "MonthlyBudgetPosition";
    private static final String DUPLICATE_NAME_MESSAGE = "A budget position with this name already exists";

    private final MonthlyBudgetRepository monthlyBudgetRepository;
    private final MonthlyBudgetPositionRepository positionRepository;
    private final FixedCostService fixedCostService;

    @Transactional(readOnly = true)
    public MonthlyBudgetResponse get(String userId) {
        log.debug("Fetching monthly budget for user={}", userId);
        return assemble(userId);
    }

    @Transactional
    public MonthlyBudgetResponse upsert(MonthlyBudgetRequest request, String userId) {
        log.info("Upserting monthly budget for user={}", userId);
        UUID existingId = monthlyBudgetRepository.findByUserId(userId)
                .map(MonthlyBudget::getId)
                .orElse(null);

        var budget = MonthlyBudget.builder()
                .id(existingId)
                .userId(userId)
                .netIncome(request.netIncome())
                .build();

        MonthlyBudget saved = monthlyBudgetRepository.save(budget);
        log.info("Upserted monthly budget id={} for user={}", saved.getId(), userId);
        return assemble(userId);
    }

    /** Positions do not require a monthly_budget row; net income simply reads as zero then. */
    @Transactional
    public MonthlyBudgetResponse createPosition(MonthlyBudgetPositionRequest request, String userId) {
        log.info("Creating budget position name='{}' for user={}", request.name(), userId);
        if (positionRepository.existsByUserIdAndNameIgnoreCase(userId, request.name())) {
            throw new IllegalArgumentException(DUPLICATE_NAME_MESSAGE);
        }

        var position = MonthlyBudgetPosition.builder()
                .userId(userId)
                .name(request.name())
                .amount(request.amount())
                .sortOrder(nextSortOrder(userId))
                .build();

        MonthlyBudgetPosition saved = savePosition(position);
        log.info("Created budget position id={} for user={}", saved.getId(), userId);
        return assemble(userId);
    }

    @Transactional
    public MonthlyBudgetResponse updatePosition(UUID id, MonthlyBudgetPositionRequest request, String userId) {
        log.info("Updating budget position id={} for user={}", id, userId);
        var existing = positionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of(POSITION_RESOURCE_NAME, id));
        if (positionRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, request.name(), id)) {
            throw new IllegalArgumentException(DUPLICATE_NAME_MESSAGE);
        }

        var position = MonthlyBudgetPosition.builder()
                .id(existing.getId())
                .userId(existing.getUserId())
                .name(request.name())
                .amount(request.amount())
                .sortOrder(existing.getSortOrder())
                .build();

        MonthlyBudgetPosition saved = savePosition(position);
        log.info("Updated budget position id={} for user={}", saved.getId(), userId);
        return assemble(userId);
    }

    @Transactional
    public MonthlyBudgetResponse deletePosition(UUID id, String userId) {
        log.info("Deleting budget position id={} for user={}", id, userId);
        positionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of(POSITION_RESOURCE_NAME, id));
        positionRepository.deleteById(id);
        log.info("Deleted budget position id={} for user={}", id, userId);
        return assemble(userId);
    }

    /** Rebuilds the full aggregate: net income (zero when no row exists), positions, fixed costs. */
    private MonthlyBudgetResponse assemble(String userId) {
        BigDecimal netIncome = monthlyBudgetRepository.findByUserId(userId)
                .map(MonthlyBudget::getNetIncome)
                .orElse(BigDecimal.ZERO);
        List<MonthlyBudgetPositionResponse> positions =
                positionRepository.findByUserIdOrderBySortOrderAscNameAsc(userId).stream()
                        .map(MonthlyBudgetPositionResponse::from)
                        .toList();
        return MonthlyBudgetResponse.of(netIncome, positions, fixedCostService.getTotalPerMonth(userId));
    }

    private int nextSortOrder(String userId) {
        return positionRepository.findTopByUserIdOrderBySortOrderDesc(userId)
                .map(position -> position.getSortOrder() + 1)
                .orElse(0);
    }

    /**
     * Flushes immediately so a concurrent duplicate that slipped past the
     * exists-pre-check surfaces here as a clean 400 instead of an unhandled
     * commit-time constraint violation (unique index on user_id + name).
     */
    private MonthlyBudgetPosition savePosition(MonthlyBudgetPosition position) {
        try {
            return positionRepository.saveAndFlush(position);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException(DUPLICATE_NAME_MESSAGE);
        }
    }
}
