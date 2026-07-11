package ch.finyo.budget;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FixedCostRepository extends JpaRepository<FixedCost, UUID> {

    List<FixedCost> findByUserId(String userId);

    Optional<FixedCost> findByIdAndUserId(UUID id, String userId);
}
