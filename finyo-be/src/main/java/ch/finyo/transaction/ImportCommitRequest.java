package ch.finyo.transaction;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Rows confirmed in the preview. The row cap mirrors the parser limit: a JSON
 * body is not covered by the multipart upload size limit.
 */
public record ImportCommitRequest(
        @NotNull UUID accountId,
        @NotNull ImportFormat format,
        boolean skipDuplicates,
        @NotEmpty @Size(max = ImportLimits.MAX_ROWS) @Valid List<ImportCommitRow> rows
) {}
