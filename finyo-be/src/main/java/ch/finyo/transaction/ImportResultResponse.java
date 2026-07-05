package ch.finyo.transaction;

import java.util.List;

public record ImportResultResponse(
        int totalRows,
        int imported,
        int skippedDuplicates,
        int failed,
        List<String> errors
) {}
