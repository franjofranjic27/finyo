package ch.finyo.savings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, UUID> {

    List<SavingsGoal> findByUserIdAndArchivedFalseOrderByTargetDateAscNameAsc(String userId);

    List<SavingsGoal> findByUserIdOrderByArchivedAscNameAsc(String userId);

    Optional<SavingsGoal> findByIdAndUserId(UUID id, String userId);
}
