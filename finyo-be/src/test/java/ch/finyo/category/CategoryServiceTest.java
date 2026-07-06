package ch.finyo.category;

import ch.finyo.common.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.times;

/**
 * Pure unit tests for CategoryService.
 *
 * Focus areas:
 *   1. Parent resolution: optional, but a given parentId must belong to the
 *      same user (cross-tenant parent linking is blocked).
 *   2. update() preserves identity fields (id, userId, createdAt).
 *   3. seedDefaultsIfEmpty(): idempotence (skip when categories exist) and the
 *      seeded catalogue split of 12 expense + 5 income categories.
 *   4. Standard multi-tenant not-found semantics.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final String USER_ID = "user-cat-1";

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Captor
    private ArgumentCaptor<Category> savedCategory;

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private Category buildCategory(UUID id, String name, CategoryType type) {
        return Category.builder()
                .id(id)
                .userId(USER_ID)
                .name(name)
                .type(type)
                .build();
    }

    private CategoryRequest requestWith(String name, UUID parentId) {
        return new CategoryRequest(name, parentId, "🛒", "#8b5cf6", CategoryType.EXPENSE);
    }

    // =========================================================================
    // getAll() / getTopLevel() / getById()
    // =========================================================================

    @Test
    void getAll_maps_user_categories_to_responses() {
        given(categoryRepository.findByUserIdOrderByTypeAscNameAsc(USER_ID))
                .willReturn(List.of(buildCategory(UUID.randomUUID(), "Groceries", CategoryType.EXPENSE)));

        List<CategoryResponse> result = categoryService.getAll(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Groceries");
    }

    @Test
    void getTopLevel_uses_the_parent_is_null_repository_query() {
        given(categoryRepository.findByUserIdAndParentIsNullOrderByTypeAscNameAsc(USER_ID))
                .willReturn(List.of());

        assertThat(categoryService.getTopLevel(USER_ID)).isEmpty();
        then(categoryRepository).should().findByUserIdAndParentIsNullOrderByTypeAscNameAsc(USER_ID);
    }

    @Test
    void getById_exposes_parent_details_in_the_response() {
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        Category parent = buildCategory(parentId, "Housing", CategoryType.EXPENSE);
        Category child = Category.builder()
                .id(childId)
                .userId(USER_ID)
                .name("Rent")
                .parent(parent)
                .type(CategoryType.EXPENSE)
                .build();
        given(categoryRepository.findByIdAndUserId(childId, USER_ID)).willReturn(Optional.of(child));

        CategoryResponse result = categoryService.getById(childId, USER_ID);

        assertThat(result.parentId()).isEqualTo(parentId);
        assertThat(result.parentName()).isEqualTo("Housing");
    }

    @Test
    void getById_throws_ResourceNotFoundException_when_category_belongs_to_another_user() {
        UUID id = UUID.randomUUID();
        given(categoryRepository.findByIdAndUserId(id, "attacker")).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getById(id, "attacker"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category");
    }

    // =========================================================================
    // create()
    // =========================================================================

    @Test
    void create_sets_userId_and_persists_all_request_fields() {
        given(categoryRepository.save(any(Category.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse result = categoryService.create(requestWith("Groceries", null), USER_ID);

        assertThat(result.name()).isEqualTo("Groceries");
        assertThat(result.icon()).isEqualTo("🛒");
        assertThat(result.color()).isEqualTo("#8b5cf6");
        assertThat(result.type()).isEqualTo(CategoryType.EXPENSE);
        then(categoryRepository).should().save(argThat(c ->
                USER_ID.equals(c.getUserId()) && c.getParent() == null));
    }

    @Test
    void create_links_the_parent_when_it_belongs_to_the_user() {
        UUID parentId = UUID.randomUUID();
        given(categoryRepository.findByIdAndUserId(parentId, USER_ID))
                .willReturn(Optional.of(buildCategory(parentId, "Housing", CategoryType.EXPENSE)));
        given(categoryRepository.save(any(Category.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse result = categoryService.create(requestWith("Rent", parentId), USER_ID);

        assertThat(result.parentId()).isEqualTo(parentId);
    }

    @Test
    void create_throws_ResourceNotFoundException_when_parent_belongs_to_another_user() {
        UUID foreignParentId = UUID.randomUUID();
        given(categoryRepository.findByIdAndUserId(foreignParentId, USER_ID)).willReturn(Optional.empty());
        CategoryRequest request = requestWith("Rent", foreignParentId);

        assertThatThrownBy(() -> categoryService.create(request, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("parent");
        then(categoryRepository).should(never()).save(any());
    }

    // =========================================================================
    // update()
    // =========================================================================

    @Test
    void update_preserves_id_owner_and_createdAt_of_the_existing_category() {
        UUID id = UUID.randomUUID();
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        Category existing = Category.builder()
                .id(id).userId(USER_ID).name("Old").type(CategoryType.EXPENSE)
                .createdAt(createdAt)
                .build();
        given(categoryRepository.findByIdAndUserId(id, USER_ID)).willReturn(Optional.of(existing));
        given(categoryRepository.save(any(Category.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse result = categoryService.update(id, requestWith("New Name", null), USER_ID);

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.name()).isEqualTo("New Name");
        assertThat(result.createdAt()).isEqualTo(createdAt);
        then(categoryRepository).should().save(argThat(c -> USER_ID.equals(c.getUserId())));
    }

    @Test
    void update_throws_ResourceNotFoundException_when_category_is_not_found() {
        UUID id = UUID.randomUUID();
        given(categoryRepository.findByIdAndUserId(id, USER_ID)).willReturn(Optional.empty());
        CategoryRequest request = requestWith("x", null);

        assertThatThrownBy(() -> categoryService.update(id, request, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        then(categoryRepository).should(never()).save(any());
    }

    @Test
    void update_throws_ResourceNotFoundException_when_new_parent_is_foreign() {
        UUID id = UUID.randomUUID();
        UUID foreignParentId = UUID.randomUUID();
        given(categoryRepository.findByIdAndUserId(id, USER_ID))
                .willReturn(Optional.of(buildCategory(id, "Mine", CategoryType.EXPENSE)));
        given(categoryRepository.findByIdAndUserId(foreignParentId, USER_ID)).willReturn(Optional.empty());
        CategoryRequest request = requestWith("Mine", foreignParentId);

        assertThatThrownBy(() -> categoryService.update(id, request, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("parent");
        then(categoryRepository).should(never()).save(any());
    }

    // =========================================================================
    // delete()
    // =========================================================================

    @Test
    void delete_removes_the_category_when_it_belongs_to_the_user() {
        UUID id = UUID.randomUUID();
        given(categoryRepository.existsByIdAndUserId(id, USER_ID)).willReturn(true);

        categoryService.delete(id, USER_ID);

        then(categoryRepository).should().deleteById(id);
    }

    @Test
    void delete_never_calls_deleteById_when_category_belongs_to_another_user() {
        UUID id = UUID.randomUUID();
        given(categoryRepository.existsByIdAndUserId(id, "attacker")).willReturn(false);

        assertThatThrownBy(() -> categoryService.delete(id, "attacker"))
                .isInstanceOf(ResourceNotFoundException.class);
        then(categoryRepository).should(never()).deleteById(any());
    }

    // =========================================================================
    // seedDefaultsIfEmpty()
    // =========================================================================

    @Test
    void seed_is_skipped_when_the_user_already_has_categories() {
        given(categoryRepository.countByUserId(USER_ID)).willReturn(3L);

        categoryService.seedDefaultsIfEmpty(USER_ID);

        then(categoryRepository).should(never()).save(any());
    }

    @Test
    void seed_creates_the_default_catalogue_of_12_expense_and_5_income_categories() {
        given(categoryRepository.countByUserId(USER_ID)).willReturn(0L);

        categoryService.seedDefaultsIfEmpty(USER_ID);

        then(categoryRepository).should(times(17)).save(savedCategory.capture());
        List<Category> seeded = savedCategory.getAllValues();
        assertThat(seeded).allSatisfy(category -> {
            assertThat(category.getUserId()).isEqualTo(USER_ID);
            assertThat(category.getParent()).isNull();
        });
        assertThat(seeded).filteredOn(c -> c.getType() == CategoryType.EXPENSE).hasSize(12);
        assertThat(seeded).filteredOn(c -> c.getType() == CategoryType.INCOME).hasSize(5);
        assertThat(seeded).extracting(Category::getName).contains("Groceries", "Salary");
    }
}
