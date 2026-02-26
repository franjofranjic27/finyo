package ch.finyoapi.category;

import ch.finyoapi.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryResponse> getAll(String userId) {
        log.debug("Fetching all categories for user={}", userId);
        return categoryRepository.findByUserIdOrderByTypeAscNameAsc(userId)
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    public List<CategoryResponse> getTopLevel(String userId) {
        log.debug("Fetching top-level categories for user={}", userId);
        return categoryRepository.findByUserIdAndParentIsNullOrderByTypeAscNameAsc(userId)
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    public CategoryResponse getById(UUID id, String userId) {
        log.debug("Fetching category id={} for user={}", id, userId);
        return categoryRepository.findByIdAndUserId(id, userId)
                .map(CategoryResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of("Category", id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request, String userId) {
        log.info("Creating category name='{}' type={} for user={}", request.name(), request.type(), userId);

        Category parent = resolveParent(request.parentId(), userId);

        Category category = Category.builder()
                .userId(userId)
                .name(request.name())
                .parent(parent)
                .icon(request.icon())
                .color(request.color())
                .type(request.type())
                .build();

        Category saved = categoryRepository.save(category);
        log.info("Created category id={} for user={}", saved.getId(), userId);
        return CategoryResponse.from(saved);
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request, String userId) {
        log.info("Updating category id={} for user={}", id, userId);
        Category existing = categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Category", id));

        Category parent = resolveParent(request.parentId(), userId);

        Category updated = Category.builder()
                .id(existing.getId())
                .userId(existing.getUserId())
                .name(request.name())
                .parent(parent)
                .icon(request.icon())
                .color(request.color())
                .type(request.type())
                .createdAt(existing.getCreatedAt())
                .build();

        Category saved = categoryRepository.save(updated);
        log.info("Updated category id={} for user={}", saved.getId(), userId);
        return CategoryResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id, String userId) {
        log.info("Deleting category id={} for user={}", id, userId);
        if (!categoryRepository.existsByIdAndUserId(id, userId)) {
            throw ResourceNotFoundException.of("Category", id);
        }
        categoryRepository.deleteById(id);
        log.info("Deleted category id={} for user={}", id, userId);
    }

    @Transactional
    public void seedDefaultsIfEmpty(String userId) {
        long count = categoryRepository.countByUserId(userId);
        if (count > 0) {
            log.debug("User={} already has {} categories, skipping seed", userId, count);
            return;
        }

        log.info("Seeding default categories for new user={}", userId);

        // EXPENSE categories
        saveDefault(userId, "Housing", "🏠", "#6366f1", CategoryType.EXPENSE, null);
        saveDefault(userId, "Groceries", "🛒", "#8b5cf6", CategoryType.EXPENSE, null);
        saveDefault(userId, "Transport", "🚗", "#3b82f6", CategoryType.EXPENSE, null);
        saveDefault(userId, "Health", "❤️", "#ef4444", CategoryType.EXPENSE, null);
        saveDefault(userId, "Insurance", "🛡️", "#f59e0b", CategoryType.EXPENSE, null);
        saveDefault(userId, "Subscriptions", "📱", "#10b981", CategoryType.EXPENSE, null);
        saveDefault(userId, "Dining & Restaurants", "🍽️", "#f97316", CategoryType.EXPENSE, null);
        saveDefault(userId, "Entertainment", "🎬", "#ec4899", CategoryType.EXPENSE, null);
        saveDefault(userId, "Clothing", "👕", "#14b8a6", CategoryType.EXPENSE, null);
        saveDefault(userId, "Education", "📚", "#6366f1", CategoryType.EXPENSE, null);
        saveDefault(userId, "Travel", "✈️", "#0ea5e9", CategoryType.EXPENSE, null);
        saveDefault(userId, "Personal Care", "💆", "#a78bfa", CategoryType.EXPENSE, null);

        // INCOME categories
        saveDefault(userId, "Salary", "💼", "#10b981", CategoryType.INCOME, null);
        saveDefault(userId, "Bonus", "🎁", "#f59e0b", CategoryType.INCOME, null);
        saveDefault(userId, "Freelance", "💻", "#6366f1", CategoryType.INCOME, null);
        saveDefault(userId, "Investment Income", "📈", "#3b82f6", CategoryType.INCOME, null);
        saveDefault(userId, "Other Income", "💰", "#ec4899", CategoryType.INCOME, null);

        log.info("Seeded default categories for user={}", userId);
    }

    private void saveDefault(String userId, String name, String icon, String color, CategoryType type, Category parent) {
        Category category = Category.builder()
                .userId(userId)
                .name(name)
                .icon(icon)
                .color(color)
                .type(type)
                .parent(parent)
                .build();
        categoryRepository.save(category);
    }

    private Category resolveParent(UUID parentId, String userId) {
        if (parentId == null) {
            return null;
        }
        return categoryRepository.findByIdAndUserId(parentId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Category (parent)", parentId));
    }
}
