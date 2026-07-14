package ch.finyo.pillar3;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Pillar3ScenarioRepository extends JpaRepository<Pillar3Scenario, UUID> {

    Optional<Pillar3Scenario> findByIdAndUserId(UUID id, String userId);

    List<Pillar3Scenario> findByUserIdOrderByCreatedAtDesc(String userId);

    boolean existsByUserId(String userId);

    boolean existsByUserIdAndIsDefaultTrue(String userId);

    Optional<Pillar3Scenario> findByUserIdAndIsDefaultTrue(String userId);
}
