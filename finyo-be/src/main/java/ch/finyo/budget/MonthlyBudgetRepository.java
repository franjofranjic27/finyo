package ch.finyo.budget;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MonthlyBudgetRepository extends JpaRepository<MonthlyBudget, UUID> {

    Optional<MonthlyBudget> findByUserId(String userId);
}
