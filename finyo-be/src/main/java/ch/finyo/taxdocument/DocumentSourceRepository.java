package ch.finyo.taxdocument;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentSourceRepository extends JpaRepository<DocumentSource, UUID> {

    List<DocumentSource> findByEnabledTrue();

    List<DocumentSource> findByUserId(String userId);

    Optional<DocumentSource> findByIdAndUserId(UUID id, String userId);
}
