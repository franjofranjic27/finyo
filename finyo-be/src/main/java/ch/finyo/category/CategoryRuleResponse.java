package ch.finyo.category;

import java.util.UUID;

public record CategoryRuleResponse(
        UUID id,
        String keyword,
        UUID categoryId,
        String categoryName,
        String categoryColor
) {
    public static CategoryRuleResponse from(CategoryRule rule) {
        return new CategoryRuleResponse(
                rule.getId(),
                rule.getKeyword(),
                rule.getCategory().getId(),
                rule.getCategory().getName(),
                rule.getCategory().getColor()
        );
    }
}
