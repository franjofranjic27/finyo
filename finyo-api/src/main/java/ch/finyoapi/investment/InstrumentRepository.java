package ch.finyoapi.investment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstrumentRepository extends JpaRepository<Instrument, UUID> {

    List<Instrument> findByUserIdOrderBySortOrderAscNameAsc(String userId);

    Optional<Instrument> findByIdAndUserId(UUID id, String userId);
}
