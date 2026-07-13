package ch.finyo.transaction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row to persist. The size caps mirror what the preview can produce
 * (descriptions are truncated to 500 characters there) and the
 * {@code external_ref} column width.
 */
public record ImportCommitRow(
        @NotNull LocalDate date,
        @NotNull BigDecimal amount,
        @Size(max = 3) String currency,
        @Size(max = 500) String description,
        @Size(max = 255) String externalRef,
        UUID categoryId
) {}
