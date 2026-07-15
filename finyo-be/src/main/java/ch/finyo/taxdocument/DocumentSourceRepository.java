package ch.finyo.taxdocument;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentSourceRepository extends JpaRepository<DocumentSource, UUID> {

    List<DocumentSource> findByEnabledTrue();

    List<DocumentSource> findByUserId(String userId);

    Optional<DocumentSource> findByIdAndUserId(UUID id, String userId);

    /**
     * Optimistic lease so a scheduled run and a manual "sync now" cannot process the
     * same folder at once. A lease older than the cutoff is considered abandoned —
     * that is how a crashed instance releases it without any cleanup job.
     *
     * @return 1 when the lease was taken, 0 when someone else holds it
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE DocumentSource s
               SET s.syncStartedAt = :now
             WHERE s.id = :id
               AND (s.syncStartedAt IS NULL OR s.syncStartedAt < :abandonedBefore)
            """)
    int acquireLease(@Param("id") UUID id,
                     @Param("now") OffsetDateTime now,
                     @Param("abandonedBefore") OffsetDateTime abandonedBefore);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE DocumentSource s SET s.syncStartedAt = NULL WHERE s.id = :id")
    void releaseLease(@Param("id") UUID id);
}
