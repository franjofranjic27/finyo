package ch.finyo.transaction;

import java.util.List;

public record ImportPreviewResponse(
        ImportFormat format,
        int totalRows,
        int newRows,
        int duplicates,
        int failed,
        List<ImportPreviewRow> rows,
        List<String> errors
) {}
