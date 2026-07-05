package ch.finyo.insurance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserInsuranceStatusRepository extends JpaRepository<UserInsuranceStatus, UUID> {

    List<UserInsuranceStatus> findByUserId(String userId);

    Optional<UserInsuranceStatus> findByUserIdAndInsuranceTypeId(String userId, UUID insuranceTypeId);
}
