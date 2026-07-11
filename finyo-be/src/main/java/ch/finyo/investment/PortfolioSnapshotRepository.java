package ch.finyo.investment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, UUID> {

    Optional<PortfolioSnapshot> findByUserIdAndSnapshotDate(String userId, LocalDate snapshotDate);

    List<PortfolioSnapshot> findByUserIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(
            String userId, LocalDate from);
}
