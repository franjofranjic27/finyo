package ch.finyo.wealth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WealthBucketRepository extends JpaRepository<WealthBucket, UUID> {

    List<WealthBucket> findByUserIdOrderBySortOrderAscNameAsc(String userId);

    Optional<WealthBucket> findByIdAndUserId(UUID id, String userId);

    boolean existsByUserIdAndName(String userId, String name);

    boolean existsByUserIdAndNameAndIdNot(String userId, String name, UUID id);
}
