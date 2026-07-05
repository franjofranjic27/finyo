package ch.finyo.tax;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaxCommuneMultiplierRepository extends JpaRepository<TaxCommuneMultiplier, Long> {

    List<TaxCommuneMultiplier> findByTaxYearAndCantonCodeOrderByCommuneNameAsc(int taxYear, String cantonCode);

    Optional<TaxCommuneMultiplier> findByTaxYearAndBfsNumber(int taxYear, int bfsNumber);

    Optional<TaxCommuneMultiplier> findTopByCantonCodeOrderByTaxYearDesc(String cantonCode);
}
