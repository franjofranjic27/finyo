package ch.finyoapi.savings;

import ch.finyoapi.account.Account;
import ch.finyoapi.account.AccountRepository;
import ch.finyoapi.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SavingsGoalService {

    private final SavingsGoalRepository savingsGoalRepository;
    private final AccountRepository accountRepository;

    public List<SavingsGoalResponse> getAll(String userId) {
        log.debug("Fetching active savings goals for user={}", userId);
        return savingsGoalRepository.findByUserIdAndArchivedFalseOrderByTargetDateAscNameAsc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<SavingsGoalResponse> getAllIncludingArchived(String userId) {
        log.debug("Fetching all savings goals (including archived) for user={}", userId);
        return savingsGoalRepository.findByUserIdOrderByArchivedAscNameAsc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public SavingsGoalResponse getById(UUID id, String userId) {
        log.debug("Fetching savings goal id={} for user={}", id, userId);
        return savingsGoalRepository.findByIdAndUserId(id, userId)
                .map(this::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.of("SavingsGoal", id));
    }

    @Transactional
    public SavingsGoalResponse create(SavingsGoalRequest request, String userId) {
        log.info("Creating savings goal name='{}' for user={}", request.name(), userId);
        Account account = resolveAccount(request.accountId(), userId);

        var goal = SavingsGoal.builder()
                .userId(userId)
                .name(request.name())
                .targetAmount(request.targetAmount())
                .currentAmount(request.currentAmount() != null ? request.currentAmount() : BigDecimal.ZERO)
                .targetDate(request.targetDate())
                .account(account)
                .icon(request.icon())
                .color(request.color())
                .archived(false)
                .build();

        SavingsGoal saved = savingsGoalRepository.save(goal);
        log.info("Created savings goal id={} for user={}", saved.getId(), userId);
        return toResponse(saved);
    }

    @Transactional
    public SavingsGoalResponse update(UUID id, SavingsGoalRequest request, String userId) {
        log.info("Updating savings goal id={} for user={}", id, userId);
        var existing = savingsGoalRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("SavingsGoal", id));

        Account account = resolveAccount(request.accountId(), userId);

        var updated = SavingsGoal.builder()
                .id(existing.getId())
                .userId(existing.getUserId())
                .name(request.name())
                .targetAmount(request.targetAmount())
                .currentAmount(request.currentAmount() != null ? request.currentAmount() : existing.getCurrentAmount())
                .targetDate(request.targetDate())
                .account(account)
                .icon(request.icon())
                .color(request.color())
                .archived(existing.isArchived())
                .build();

        SavingsGoal saved = savingsGoalRepository.save(updated);
        log.info("Updated savings goal id={} for user={}", saved.getId(), userId);
        return toResponse(saved);
    }

    @Transactional
    public SavingsGoalResponse archive(UUID id, String userId) {
        log.info("Archiving savings goal id={} for user={}", id, userId);
        var existing = savingsGoalRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("SavingsGoal", id));

        var archived = SavingsGoal.builder()
                .id(existing.getId())
                .userId(existing.getUserId())
                .name(existing.getName())
                .targetAmount(existing.getTargetAmount())
                .currentAmount(existing.getCurrentAmount())
                .targetDate(existing.getTargetDate())
                .account(existing.getAccount())
                .icon(existing.getIcon())
                .color(existing.getColor())
                .archived(true)
                .build();

        SavingsGoal saved = savingsGoalRepository.save(archived);
        log.info("Archived savings goal id={} for user={}", saved.getId(), userId);
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID id, String userId) {
        log.info("Deleting savings goal id={} for user={}", id, userId);
        savingsGoalRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("SavingsGoal", id));
        savingsGoalRepository.deleteById(id);
        log.info("Deleted savings goal id={} for user={}", id, userId);
    }

    private Account resolveAccount(UUID accountId, String userId) {
        if (accountId == null) {
            return null;
        }
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Account", accountId));
    }

    private SavingsGoalResponse toResponse(SavingsGoal goal) {
        double progressPercentage = computeProgress(goal.getCurrentAmount(), goal.getTargetAmount());
        BigDecimal monthlyRequired = computeMonthlyRequired(goal.getCurrentAmount(), goal.getTargetAmount(), goal.getTargetDate());

        return new SavingsGoalResponse(
                goal.getId(),
                goal.getName(),
                goal.getTargetAmount(),
                goal.getCurrentAmount(),
                goal.getTargetDate(),
                goal.getAccount() != null ? goal.getAccount().getId() : null,
                goal.getAccount() != null ? goal.getAccount().getName() : null,
                goal.getIcon(),
                goal.getColor(),
                goal.isArchived(),
                progressPercentage,
                monthlyRequired
        );
    }

    private double computeProgress(BigDecimal current, BigDecimal target) {
        if (target == null || target.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        double raw = current.divide(target, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
        return Math.min(raw, 100.0);
    }

    private BigDecimal computeMonthlyRequired(BigDecimal current, BigDecimal target, LocalDate targetDate) {
        if (targetDate == null) {
            return null;
        }
        LocalDate today = LocalDate.now();
        long months = ChronoUnit.MONTHS.between(today.withDayOfMonth(1), targetDate.withDayOfMonth(1));
        if (months <= 0) {
            return null;
        }
        BigDecimal remaining = target.subtract(current);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return remaining.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
    }
}
