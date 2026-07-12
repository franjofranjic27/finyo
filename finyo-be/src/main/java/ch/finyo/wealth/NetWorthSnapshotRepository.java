package ch.finyo.wealth;

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

public interface NetWorthSnapshotRepository extends JpaRepository<NetWorthSnapshot, UUID> {

    List<NetWorthSnapshot> findByUserIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(
            String userId, LocalDate from);

    /** Earliest snapshot in [from, before) — the year-to-date baseline. */
    Optional<NetWorthSnapshot> findFirstByUserIdAndSnapshotDateGreaterThanEqualAndSnapshotDateLessThanOrderBySnapshotDateAsc(
            String userId, LocalDate from, LocalDate before);

    /**
     * Atomically inserts or updates the snapshot for a user and day. Mirrors
     * the portfolio_snapshot upsert: a single ON CONFLICT statement avoids the
     * read-then-write race without poisoning a surrounding transaction with a
     * constraint violation.
     */
    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO net_worth_snapshot (id, user_id, snapshot_date, total, created_at)
            VALUES (gen_random_uuid(), :userId, :snapshotDate, :total, NOW())
            ON CONFLICT (user_id, snapshot_date)
            DO UPDATE SET total = EXCLUDED.total
            """)
    void upsert(@Param("userId") String userId,
                @Param("snapshotDate") LocalDate snapshotDate,
                @Param("total") BigDecimal total);
}
