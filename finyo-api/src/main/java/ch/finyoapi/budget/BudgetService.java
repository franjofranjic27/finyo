package ch.finyoapi.budget;

import ch.finyoapi.category.CategoryRepository;
import ch.finyoapi.common.ResourceNotFoundException;
import ch.finyoapi.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;

    public List<BudgetResponse> getAll(String userId) {
        log.debug("Fetching all budgets for user={}", userId);
        return budgetRepository.findByUserIdOrderByCategoryNameAsc(userId)
                .stream()
                .map(BudgetResponse::from)
                .toList();
    }

    public BudgetResponse getById(UUID id, String userId) {
        log.debug("Fetching budget id={} for user={}", id, userId);
        return budgetRepository.findByIdAndUserId(id, userId)
                .map(BudgetResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of("Budget", id));
    }

    public List<BudgetStatusResponse> getCurrentStatus(String userId) {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);

        log.debug("Computing current budget status for user={} monthStart={}", userId, monthStart);

        List<Budget> activeBudgets = budgetRepository.findActiveBudgetsForUserAndDate(userId, today);

        List<Object[]> allSpending = transactionRepository.sumExpensesByCategoryForPeriod(userId, monthStart, today);

        return activeBudgets.stream().map(budget -> {
            BigDecimal spent = allSpending.stream()
                    .filter(row -> budget.getCategory().getId().equals(row[0]))
                    .map(row -> ((BigDecimal) row[1]).abs())
                    .findFirst()
                    .orElse(BigDecimal.ZERO);

            BigDecimal remaining = budget.getAmount().subtract(spent);
            double percentage = budget.getAmount().compareTo(BigDecimal.ZERO) == 0 ? 0.0
                    : spent.divide(budget.getAmount(), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .doubleValue();

            return new BudgetStatusResponse(
                    budget.getId(),
                    budget.getCategory().getId(),
                    budget.getCategory().getName(),
                    budget.getCategory().getColor(),
                    budget.getAmount(),
                    spent,
                    remaining,
                    Math.min(percentage, 999.99),
                    remaining.compareTo(BigDecimal.ZERO) < 0,
                    budget.getPeriod(),
                    budget.getValidFrom(),
                    budget.getValidUntil()
            );
        }).toList();
    }

    @Transactional
    public BudgetResponse create(BudgetRequest request, String userId) {
        log.info("Creating budget categoryId={} for user={}", request.categoryId(), userId);
        var category = categoryRepository.findByIdAndUserId(request.categoryId(), userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Category", request.categoryId()));

        var budget = Budget.builder()
                .userId(userId)
                .category(category)
                .amount(request.amount())
                .period(request.period() != null ? request.period() : BudgetPeriod.MONTHLY)
                .validFrom(request.validFrom())
                .validUntil(request.validUntil())
                .build();

        Budget saved = budgetRepository.save(budget);
        log.info("Created budget id={} for user={}", saved.getId(), userId);
        return BudgetResponse.from(saved);
    }

    @Transactional
    public BudgetResponse update(UUID id, BudgetRequest request, String userId) {
        log.info("Updating budget id={} for user={}", id, userId);
        var existing = budgetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Budget", id));

        var category = categoryRepository.findByIdAndUserId(request.categoryId(), userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Category", request.categoryId()));

        var updated = Budget.builder()
                .id(existing.getId())
                .userId(userId)
                .category(category)
                .amount(request.amount())
                .period(request.period() != null ? request.period() : existing.getPeriod())
                .validFrom(request.validFrom())
                .validUntil(request.validUntil())
                .build();

        Budget saved = budgetRepository.save(updated);
        log.info("Updated budget id={} for user={}", saved.getId(), userId);
        return BudgetResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id, String userId) {
        log.info("Deleting budget id={} for user={}", id, userId);
        budgetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Budget", id));
        budgetRepository.deleteById(id);
        log.info("Deleted budget id={} for user={}", id, userId);
    }
}
