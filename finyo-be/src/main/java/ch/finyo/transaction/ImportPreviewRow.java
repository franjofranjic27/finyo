package ch.finyo.transaction;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One parsed statement row of an import preview. {@code index} is the 1-based
 * position within the preview result, {@code duplicate} flags rows that
 * already exist (by external reference, or by date/amount/description for
 * files without references).
 */
public record ImportPreviewRow(
        int index,
        LocalDate date,
        BigDecimal amount,
        @Nullable String currency,
        @Nullable String description,
        @Nullable String counterparty,
        @Nullable String externalRef,
        boolean duplicate,
        @Nullable UUID suggestedCategoryId,
        @Nullable String suggestedCategoryName
) {}
