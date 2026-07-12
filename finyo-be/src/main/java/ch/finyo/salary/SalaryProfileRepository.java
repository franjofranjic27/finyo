package ch.finyo.salary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SalaryProfileRepository extends JpaRepository<SalaryProfile, UUID> {

    Optional<SalaryProfile> findByUserId(String userId);
}
