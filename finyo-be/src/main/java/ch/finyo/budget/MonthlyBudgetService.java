package ch.finyo.budget;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyBudgetService {

    private final MonthlyBudgetRepository monthlyBudgetRepository;
    private final FixedCostService fixedCostService;

    @Transactional(readOnly = true)
    public MonthlyBudgetResponse get(String userId) {
        log.debug("Fetching monthly budget for user={}", userId);
        BigDecimal fixedCostsPerMonth = fixedCostService.getTotalPerMonth(userId);
        return monthlyBudgetRepository.findByUserId(userId)
                .map(budget -> MonthlyBudgetResponse.from(budget, fixedCostsPerMonth))
                .orElseGet(() -> MonthlyBudgetResponse.empty(fixedCostsPerMonth));
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
                .savings(request.savings())
                .investing(request.investing())
                .pillar3a(request.pillar3a())
                .taxReserve(request.taxReserve())
                .workCosts(request.workCosts())
                .build();

        MonthlyBudget saved = monthlyBudgetRepository.save(budget);
        log.info("Upserted monthly budget id={} for user={}", saved.getId(), userId);
        return MonthlyBudgetResponse.from(saved, fixedCostService.getTotalPerMonth(userId));
    }
}
