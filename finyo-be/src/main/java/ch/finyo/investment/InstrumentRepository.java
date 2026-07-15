package ch.finyo.investment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstrumentRepository extends JpaRepository<Instrument, UUID> {

    /**
     * Across all users on purpose: a price is a market fact, not user data, so the same ISIN
     * held by two people is worth exactly one request to the vendor rather than two.
     */
    @Query("SELECT DISTINCT i.isin FROM Instrument i WHERE i.isin IS NOT NULL")
    List<String> findDistinctIsins();

    List<Instrument> findByUserIdOrderBySortOrderAscNameAsc(String userId);

    List<Instrument> findByIdInAndUserId(Collection<UUID> ids, String userId);

    Optional<Instrument> findByIdAndUserId(UUID id, String userId);

    Optional<Instrument> findFirstByUserIdAndIsinIgnoreCase(String userId, String isin);

    Optional<Instrument> findFirstByUserIdAndValor(String userId, String valor);
}
