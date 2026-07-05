package ch.finyo.tax;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaxDeadlineRepository extends JpaRepository<TaxDeadline, UUID> {

    List<TaxDeadline> findByTaxYearIdAndUserIdOrderByDueDateAsc(UUID taxYearId, String userId);

    Optional<TaxDeadline> findByIdAndUserId(UUID id, String userId);
}
