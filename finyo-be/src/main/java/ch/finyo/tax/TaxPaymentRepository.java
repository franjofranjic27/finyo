package ch.finyo.tax;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaxPaymentRepository extends JpaRepository<TaxPayment, UUID> {

    List<TaxPayment> findByTaxYearIdAndUserIdOrderByPaymentDateAsc(UUID taxYearId, String userId);

    Optional<TaxPayment> findByIdAndUserId(UUID id, String userId);
}
