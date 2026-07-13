package ch.finyo.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CategoryRuleRequest(
        @NotBlank @Size(max = 100) String keyword,
        @NotNull UUID categoryId
) {}
