package ch.finyo.transaction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row to persist. The size caps mirror what the preview can produce
 * (descriptions and references are truncated there) and the
 * {@code external_ref} column width.
 */
public record ImportCommitRow(
        @NotNull LocalDate date,
        @NotNull BigDecimal amount,
        @Size(max = 3) @Nullable String currency,
        @Size(max = ImportLimits.MAX_DESCRIPTION_LENGTH) @Nullable String description,
        @Size(max = ImportLimits.MAX_EXTERNAL_REF_LENGTH) @Nullable String externalRef,
        @Nullable UUID categoryId
) implements DuplicateCandidate {}
