package ch.finyo.taxdocument;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByUserIdOrderByCreatedAtDesc(String userId);

    /** Ownership check: cross-user access surfaces as 404, as everywhere else. */
    Optional<Document> findByIdAndUserId(UUID id, String userId);

    /** The natural key of a remote file — item IDs are unique per drive, not globally. */
    Optional<Document> findBySourceIdAndSourceItemId(UUID sourceId, String sourceItemId);
}
