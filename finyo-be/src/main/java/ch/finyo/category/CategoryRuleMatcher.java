package ch.finyo.category;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Immutable, pre-sorted snapshot of one user's category rules.
 *
 * <p>Matching is a case-insensitive substring check ({@link Locale#ROOT}, no
 * diacritic folding). Rules are ordered by keyword length descending so the
 * longest — i.e. most specific — keyword wins ("migros restaurant" beats
 * "migros").
 */
public final class CategoryRuleMatcher {

    private static final CategoryRuleMatcher EMPTY = new CategoryRuleMatcher(List.of());

    private final List<KeywordRule> rules;

    private record KeywordRule(String lowerCaseKeyword, Category category) {}

    private CategoryRuleMatcher(List<KeywordRule> rules) {
        this.rules = rules;
    }

    public static CategoryRuleMatcher empty() {
        return EMPTY;
    }

    public static CategoryRuleMatcher of(List<CategoryRule> categoryRules) {
        List<KeywordRule> sorted = categoryRules.stream()
                .map(rule -> new KeywordRule(rule.getKeyword().toLowerCase(Locale.ROOT), rule.getCategory()))
                .sorted(Comparator.comparingInt((KeywordRule rule) -> rule.lowerCaseKeyword().length()).reversed())
                .toList();
        return sorted.isEmpty() ? EMPTY : new CategoryRuleMatcher(sorted);
    }

    /** Returns the category of the longest keyword contained in {@code text}, if any. */
    public Optional<Category> match(String text) {
        if (text == null || text.isBlank() || rules.isEmpty()) {
            return Optional.empty();
        }
        String lowerCaseText = text.toLowerCase(Locale.ROOT);
        return rules.stream()
                .filter(rule -> lowerCaseText.contains(rule.lowerCaseKeyword()))
                .map(KeywordRule::category)
                .findFirst();
    }
}
