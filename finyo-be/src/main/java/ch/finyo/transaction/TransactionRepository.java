package ch.finyo.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByUserId(String userId, Pageable pageable);

    Page<Transaction> findByUserIdAndDateBetween(String userId, LocalDate from, LocalDate to, Pageable pageable);

    Optional<Transaction> findByIdAndUserId(UUID id, String userId);

    List<Transaction> findByUserIdAndDateBetweenOrderByDateDesc(String userId, LocalDate from, LocalDate to);

    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.date BETWEEN :from AND :to AND t.amount < 0")
    List<Transaction> findExpensesByUserIdAndDateBetween(
            @Param("userId") String userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // uncategorized expenses are included as a NULL category-id group so the
    // breakdown always sums up to the spending total
    @Query("SELECT t.category.id, SUM(t.amount) FROM Transaction t WHERE t.userId = :userId AND t.date BETWEEN :from AND :to AND t.amount < 0 GROUP BY t.category.id")
    List<Object[]> sumExpensesByCategoryForPeriod(
            @Param("userId") String userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("SELECT EXTRACT(YEAR FROM t.date) as year, EXTRACT(MONTH FROM t.date) as month, SUM(CASE WHEN t.amount < 0 THEN t.amount ELSE 0 END) as expenses, SUM(CASE WHEN t.amount > 0 THEN t.amount ELSE 0 END) as income FROM Transaction t WHERE t.userId = :userId AND t.date BETWEEN :from AND :to GROUP BY year, month ORDER BY year, month")
    List<Object[]> monthlyTotalsForPeriod(
            @Param("userId") String userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    boolean existsByUserIdAndDateAndAmountAndDescription(
            String userId,
            LocalDate date,
            BigDecimal amount,
            String description);

    boolean existsByUserIdAndExternalRef(String userId, String externalRef);
}
