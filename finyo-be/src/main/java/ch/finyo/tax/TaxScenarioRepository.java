package ch.finyo.tax;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaxScenarioRepository extends JpaRepository<TaxScenario, UUID> {

    Optional<TaxScenario> findByIdAndUserId(UUID id, String userId);

    List<TaxScenario> findByTaxYearIdAndUserIdOrderByCreatedAtDesc(UUID taxYearId, String userId);

    Optional<TaxScenario> findByTaxYearIdAndUserIdAndIsDefaultTrue(UUID taxYearId, String userId);

    boolean existsByTaxYearIdAndUserIdAndIsDefaultTrue(UUID taxYearId, String userId);
}
