package ch.finyo.tax;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaxYearRepository extends JpaRepository<TaxYear, UUID> {

    List<TaxYear> findByUserIdOrderByTaxYearDesc(String userId);

    Optional<TaxYear> findByUserIdAndTaxYear(String userId, int taxYear);

    Optional<TaxYear> findByIdAndUserId(UUID id, String userId);
}
