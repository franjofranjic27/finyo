package ch.finyo.investment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, UUID> {

    Optional<PortfolioSnapshot> findByUserIdAndSnapshotDate(String userId, LocalDate snapshotDate);

    List<PortfolioSnapshot> findByUserIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(
            String userId, LocalDate from);

    /**
     * Atomically inserts or updates the snapshot for a user and day. The single
     * ON CONFLICT statement avoids the read-then-write race (e.g. React
     * StrictMode double fetch) without ever poisoning a surrounding
     * transaction with a constraint violation.
     */
    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO portfolio_snapshot (id, user_id, snapshot_date, total_value, total_cost, created_at)
            VALUES (gen_random_uuid(), :userId, :snapshotDate, :totalValue, :totalCost, NOW())
            ON CONFLICT (user_id, snapshot_date)
            DO UPDATE SET total_value = EXCLUDED.total_value, total_cost = EXCLUDED.total_cost
            """)
    void upsert(@Param("userId") String userId,
                @Param("snapshotDate") LocalDate snapshotDate,
                @Param("totalValue") BigDecimal totalValue,
                @Param("totalCost") BigDecimal totalCost);
}
