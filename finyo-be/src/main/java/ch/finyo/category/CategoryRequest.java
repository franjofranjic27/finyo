package ch.finyo.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CategoryRequest(
        @NotBlank @Size(max = 100) String name,
        UUID parentId,
        @Size(max = 50) String icon,
        @Size(max = 7) String color,
        @NotNull CategoryType type
) {}
