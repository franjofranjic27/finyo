package ch.finyo.category;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRuleRepository extends JpaRepository<CategoryRule, UUID> {

    // category is fetched eagerly so both DTO mapping and the detached
    // CategoryRuleMatcher can read it without an open session
    @EntityGraph(attributePaths = "category")
    List<CategoryRule> findByUserIdOrderByKeywordAsc(String userId);

    Optional<CategoryRule> findByIdAndUserId(UUID id, String userId);

    boolean existsByUserIdAndKeywordIgnoreCase(String userId, String keyword);
}
