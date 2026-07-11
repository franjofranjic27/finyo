package ch.finyo.pillar3;

import java.util.List;

public record Pillar3ProductImportResult(
        int totalRows,
        int created,
        int updated,
        int failed,
        List<String> errors
) {}
