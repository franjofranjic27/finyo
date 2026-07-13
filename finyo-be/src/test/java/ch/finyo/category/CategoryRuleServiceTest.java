package ch.finyo.category;

import ch.finyo.common.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

/**
 * Pure unit tests for CategoryRuleService and CategoryRuleMatcher.
 *
 * Matcher semantics under test: case-insensitive substring matching
 * (Locale.ROOT, no diacritic folding), longest keyword wins, no match on
 * blank input. CRUD side: category ownership (404), case-insensitive
 * duplicate keywords (409 via IllegalStateException) and keyword trimming.
 */
@ExtendWith(MockitoExtension.class)
class CategoryRuleServiceTest {

    private static final String USER_ID = "user-rules-1";

    @Mock
    private CategoryRuleRepository categoryRuleRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryRuleService categoryRuleService;

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private Category buildCategory(String name) {
        return Category.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .name(name)
                .type(CategoryType.EXPENSE)
                .build();
    }

    private CategoryRule buildRule(String keyword, Category category) {
        return CategoryRule.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .keyword(keyword)
                .category(category)
                .build();
    }

    private CategoryRuleMatcher matcherWith(CategoryRule... rules) {
        given(categoryRuleRepository.findByUserIdOrderByKeywordAsc(USER_ID)).willReturn(List.of(rules));
        return categoryRuleService.loadMatcher(USER_ID);
    }

    // =========================================================================
    // Matcher
    // =========================================================================

    @Test
    void match_is_case_insensitive() {
        Category groceries = buildCategory("Groceries");
        CategoryRuleMatcher matcher = matcherWith(buildRule("Migros", groceries));

        assertThat(matcher.match("Einkauf MIGROS Zuerich")).contains(groceries);
        assertThat(matcher.match("einkauf migros zuerich")).contains(groceries);
    }

    @Test
    void match_finds_the_keyword_as_substring_anywhere_in_the_text() {
        Category transport = buildCategory("Transport");
        CategoryRuleMatcher matcher = matcherWith(buildRule("sbb", transport));

        assertThat(matcher.match("EZBillett SBB-Mobile CHF 12.40")).contains(transport);
    }

    @Test
    void longest_keyword_wins_when_multiple_rules_match() {
        Category groceries = buildCategory("Groceries");
        Category dining = buildCategory("Dining");
        CategoryRuleMatcher matcher = matcherWith(
                buildRule("migros", groceries),
                buildRule("migros restaurant", dining));

        assertThat(matcher.match("MIGROS RESTAURANT ZUERICH")).contains(dining);
        assertThat(matcher.match("MIGROS SUPERMARKT")).contains(groceries);
    }

    @Test
    void umlaut_keywords_match_exactly_without_diacritic_folding() {
        Category housing = buildCategory("Housing");
        CategoryRuleMatcher matcher = matcherWith(buildRule("Zürich Wohnen", housing));

        assertThat(matcher.match("Miete ZÜRICH WOHNEN AG")).contains(housing);
        // no folding: the ascii variant does not match
        assertThat(matcher.match("Miete ZUERICH WOHNEN AG")).isEmpty();
    }

    @Test
    void match_returns_empty_for_unmatched_blank_or_null_text() {
        CategoryRuleMatcher matcher = matcherWith(buildRule("migros", buildCategory("Groceries")));

        assertThat(matcher.match("Coop Pronto")).isEmpty();
        assertThat(matcher.match("  ")).isEmpty();
        assertThat(matcher.match(null)).isEmpty();
    }

    @Test
    void keyword_in_the_counterparty_part_of_a_combined_text_matches() {
        // the import concatenates description and counterparty before matching
        Category groceries = buildCategory("Groceries");
        CategoryRuleMatcher matcher = matcherWith(buildRule("migros", groceries));

        String description = "Kartenzahlung 15.03.2025";
        String counterparty = "Migros Testfiliale";
        assertThat(matcher.match(description + " " + counterparty)).contains(groceries);
    }

    @Test
    void empty_matcher_never_matches() {
        given(categoryRuleRepository.findByUserIdOrderByKeywordAsc(USER_ID)).willReturn(List.of());

        assertThat(categoryRuleService.loadMatcher(USER_ID).match("anything")).isEmpty();
    }

    // =========================================================================
    // CRUD
    // =========================================================================

    @Test
    void create_persists_a_trimmed_keyword_for_an_owned_category() {
        Category groceries = buildCategory("Groceries");
        given(categoryRepository.findByIdAndUserId(groceries.getId(), USER_ID)).willReturn(Optional.of(groceries));
        given(categoryRuleRepository.existsByUserIdAndKeywordIgnoreCase(USER_ID, "migros")).willReturn(false);
        given(categoryRuleRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        CategoryRuleResponse response = categoryRuleService.create(
                new CategoryRuleRequest("  migros ", groceries.getId()), USER_ID);

        assertThat(response.keyword()).isEqualTo("migros");
        assertThat(response.categoryId()).isEqualTo(groceries.getId());
        assertThat(response.categoryName()).isEqualTo("Groceries");
    }

    @Test
    void create_with_a_foreign_category_throws_not_found() {
        UUID foreignCategoryId = UUID.randomUUID();
        given(categoryRepository.findByIdAndUserId(foreignCategoryId, USER_ID)).willReturn(Optional.empty());
        CategoryRuleRequest request = new CategoryRuleRequest("migros", foreignCategoryId);

        assertThatThrownBy(() -> categoryRuleService.create(request, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category");
        then(categoryRuleRepository).should(never()).save(any());
    }

    @Test
    void create_with_a_duplicate_keyword_ignoring_case_throws_conflict() {
        Category groceries = buildCategory("Groceries");
        given(categoryRepository.findByIdAndUserId(groceries.getId(), USER_ID)).willReturn(Optional.of(groceries));
        given(categoryRuleRepository.existsByUserIdAndKeywordIgnoreCase(USER_ID, "MIGROS")).willReturn(true);
        CategoryRuleRequest request = new CategoryRuleRequest("MIGROS", groceries.getId());

        assertThatThrownBy(() -> categoryRuleService.create(request, USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
        then(categoryRuleRepository).should(never()).save(any());
    }

    @Test
    void update_keeps_the_own_keyword_without_raising_a_duplicate_conflict() {
        Category groceries = buildCategory("Groceries");
        CategoryRule existing = buildRule("migros", groceries);
        given(categoryRuleRepository.findByIdAndUserId(existing.getId(), USER_ID)).willReturn(Optional.of(existing));
        given(categoryRepository.findByIdAndUserId(groceries.getId(), USER_ID)).willReturn(Optional.of(groceries));
        given(categoryRuleRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        // same keyword, different casing: must not be treated as a duplicate
        CategoryRuleResponse response = categoryRuleService.update(
                existing.getId(), new CategoryRuleRequest("MIGROS", groceries.getId()), USER_ID);

        assertThat(response.keyword()).isEqualTo("MIGROS");
        then(categoryRuleRepository).should(never()).existsByUserIdAndKeywordIgnoreCase(any(), any());
    }

    @Test
    void update_to_an_existing_other_keyword_throws_conflict() {
        Category groceries = buildCategory("Groceries");
        CategoryRule existing = buildRule("migros", groceries);
        given(categoryRuleRepository.findByIdAndUserId(existing.getId(), USER_ID)).willReturn(Optional.of(existing));
        given(categoryRepository.findByIdAndUserId(groceries.getId(), USER_ID)).willReturn(Optional.of(groceries));
        given(categoryRuleRepository.existsByUserIdAndKeywordIgnoreCase(USER_ID, "coop")).willReturn(true);
        UUID ruleId = existing.getId();
        CategoryRuleRequest request = new CategoryRuleRequest("coop", groceries.getId());

        assertThatThrownBy(() -> categoryRuleService.update(ruleId, request, USER_ID))
                .isInstanceOf(IllegalStateException.class);
        then(categoryRuleRepository).should(never()).save(any());
    }

    @Test
    void update_of_a_foreign_rule_throws_not_found() {
        UUID foreignRuleId = UUID.randomUUID();
        given(categoryRuleRepository.findByIdAndUserId(foreignRuleId, USER_ID)).willReturn(Optional.empty());
        CategoryRuleRequest request = new CategoryRuleRequest("migros", UUID.randomUUID());

        assertThatThrownBy(() -> categoryRuleService.update(foreignRuleId, request, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_of_a_foreign_rule_throws_not_found() {
        UUID foreignRuleId = UUID.randomUUID();
        given(categoryRuleRepository.findByIdAndUserId(foreignRuleId, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryRuleService.delete(foreignRuleId, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        then(categoryRuleRepository).should(never()).delete(any(CategoryRule.class));
    }

    @Test
    void delete_removes_an_owned_rule() {
        CategoryRule existing = buildRule("migros", buildCategory("Groceries"));
        given(categoryRuleRepository.findByIdAndUserId(existing.getId(), USER_ID)).willReturn(Optional.of(existing));

        categoryRuleService.delete(existing.getId(), USER_ID);

        then(categoryRuleRepository).should().delete(existing);
    }
}
