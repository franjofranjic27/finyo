package ch.finyoapi.budget;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    List<Budget> findByUserIdOrderByCategoryNameAsc(String userId);

    @Query("SELECT b FROM Budget b WHERE b.userId = :userId AND b.validFrom <= :date AND (b.validUntil IS NULL OR b.validUntil >= :date)")
    List<Budget> findActiveBudgetsForUserAndDate(
            @Param("userId") String userId,
            @Param("date") LocalDate date);

    Optional<Budget> findByIdAndUserId(UUID id, String userId);
}
