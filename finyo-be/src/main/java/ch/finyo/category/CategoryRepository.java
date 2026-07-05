package ch.finyo.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByUserIdOrderByTypeAscNameAsc(String userId);

    List<Category> findByUserIdAndParentIsNullOrderByTypeAscNameAsc(String userId);

    Optional<Category> findByIdAndUserId(UUID id, String userId);

    boolean existsByIdAndUserId(UUID id, String userId);

    long countByUserId(String userId);
}
