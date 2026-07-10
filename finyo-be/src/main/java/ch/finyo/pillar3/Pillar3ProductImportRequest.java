package ch.finyo.pillar3;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Bulk import payload. Rows are deliberately NOT cascade-validated here:
 * each row is validated individually in the service so a single invalid row
 * is reported in the result instead of failing the whole batch with 400.
 */
public record Pillar3ProductImportRequest(
        @NotEmpty List<Pillar3ProductRequest> products
) {}
