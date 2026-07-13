package ch.finyo.category;

import ch.finyo.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryRuleService {

    private static final String RESOURCE_NAME = "CategoryRule";

    private final CategoryRuleRepository categoryRuleRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryRuleResponse> getAll(String userId) {
        log.debug("Fetching all category rules for user={}", userId);
        return categoryRuleRepository.findByUserIdOrderByKeywordAsc(userId)
                .stream()
                .map(CategoryRuleResponse::from)
                .toList();
    }

    /**
     * Loads all rules of the user into an immutable matcher. Load once per
     * import and reuse it for every row — matching itself never touches the
     * database.
     */
    @Transactional(readOnly = true)
    public CategoryRuleMatcher loadMatcher(String userId) {
        return CategoryRuleMatcher.of(categoryRuleRepository.findByUserIdOrderByKeywordAsc(userId));
    }

    @Transactional
    public CategoryRuleResponse create(CategoryRuleRequest request, String userId) {
        log.info("Creating category rule categoryId={} for user={}", request.categoryId(), userId);
        String keyword = request.keyword().strip();

        Category category = resolveCategory(request.categoryId(), userId);
        if (categoryRuleRepository.existsByUserIdAndKeywordIgnoreCase(userId, keyword)) {
            throw new IllegalStateException("A rule with this keyword already exists");
        }

        CategoryRule saved = categoryRuleRepository.save(CategoryRule.builder()
                .userId(userId)
                .keyword(keyword)
                .category(category)
                .build());
        log.info("Created category rule id={} for user={}", saved.getId(), userId);
        return CategoryRuleResponse.from(saved);
    }

    @Transactional
    public CategoryRuleResponse update(UUID id, CategoryRuleRequest request, String userId) {
        log.info("Updating category rule id={} for user={}", id, userId);
        CategoryRule existing = categoryRuleRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE_NAME, id));
        String keyword = request.keyword().strip();

        Category category = resolveCategory(request.categoryId(), userId);
        boolean keywordChanged = !existing.getKeyword().equalsIgnoreCase(keyword);
        if (keywordChanged && categoryRuleRepository.existsByUserIdAndKeywordIgnoreCase(userId, keyword)) {
            throw new IllegalStateException("A rule with this keyword already exists");
        }

        CategoryRule saved = categoryRuleRepository.save(CategoryRule.builder()
                .id(existing.getId())
                .userId(existing.getUserId())
                .keyword(keyword)
                .category(category)
                .createdAt(existing.getCreatedAt())
                .build());
        log.info("Updated category rule id={} for user={}", saved.getId(), userId);
        return CategoryRuleResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id, String userId) {
        log.info("Deleting category rule id={} for user={}", id, userId);
        CategoryRule existing = categoryRuleRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE_NAME, id));
        categoryRuleRepository.delete(existing);
        log.info("Deleted category rule id={} for user={}", id, userId);
    }

    private Category resolveCategory(UUID categoryId, String userId) {
        return categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Category", categoryId));
    }
}
