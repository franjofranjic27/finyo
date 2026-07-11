package ch.finyo.investment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PositionRepository extends JpaRepository<Position, UUID> {

    List<Position> findByUserId(String userId);

    Optional<Position> findByIdAndUserId(UUID id, String userId);

    Optional<Position> findByUserIdAndInstrumentId(String userId, UUID instrumentId);
}
