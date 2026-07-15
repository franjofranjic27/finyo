package ch.finyo.investment;

import ch.finyo.common.money.CurrencyCode;
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

    /**
     * Distinct currencies held, tenant-free like the ISINs and for the same reason — an exchange
     * rate is a market fact. CHF is filtered out in Java by the caller: it needs no rate, and
     * comparing a converted column in JPQL is more surprise than the one-line filter is worth.
     */
    @Query("SELECT DISTINCT i.currency FROM Instrument i WHERE i.currency IS NOT NULL")
    List<CurrencyCode> findDistinctCurrencies();

    List<Instrument> findByUserIdOrderBySortOrderAscNameAsc(String userId);

    List<Instrument> findByIdInAndUserId(Collection<UUID> ids, String userId);

    Optional<Instrument> findByIdAndUserId(UUID id, String userId);

    Optional<Instrument> findFirstByUserIdAndIsinIgnoreCase(String userId, String isin);

    Optional<Instrument> findFirstByUserIdAndValor(String userId, String valor);
}
