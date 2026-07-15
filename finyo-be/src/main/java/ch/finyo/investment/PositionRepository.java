package ch.finyo.investment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PositionRepository extends JpaRepository<Position, UUID> {

    List<Position> findByUserId(String userId);

    /** Everyone who holds anything — the nightly snapshot job iterates these. */
    @Query("SELECT DISTINCT p.userId FROM Position p")
    List<String> findDistinctUserIds();

    Optional<Position> findByIdAndUserId(UUID id, String userId);

    Optional<Position> findByUserIdAndInstrumentId(String userId, UUID instrumentId);
}
