package ch.finyo.category;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        UUID parentId,
        String parentName,
        String icon,
        String color,
        CategoryType type,
        OffsetDateTime createdAt
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getParent() != null ? category.getParent().getId() : null,
                category.getParent() != null ? category.getParent().getName() : null,
                category.getIcon(),
                category.getColor(),
                category.getType(),
                category.getCreatedAt()
        );
    }
}
