package ch.finyo.budget;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MonthlyBudgetPositionRepository extends JpaRepository<MonthlyBudgetPosition, UUID> {

    List<MonthlyBudgetPosition> findByUserIdOrderBySortOrderAscNameAsc(String userId);

    Optional<MonthlyBudgetPosition> findByIdAndUserId(UUID id, String userId);

    boolean existsByUserIdAndNameIgnoreCase(String userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(String userId, String name, UUID id);

    Optional<MonthlyBudgetPosition> findTopByUserIdOrderBySortOrderDesc(String userId);
}
