package ch.finyo.investment;

import java.util.List;

public record BulkImportResultResponse(
        int imported,
        int failed,
        List<String> errors
) {}
