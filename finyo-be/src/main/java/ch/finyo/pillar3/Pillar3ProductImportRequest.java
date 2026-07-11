package ch.finyo.pillar3;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Bulk import payload. Rows are deliberately NOT cascade-validated here:
 * each row is validated individually in the service so a single invalid row
 * is reported in the result instead of failing the whole batch with 400.
 * The size cap bounds memory usage per request; larger catalogs are
 * imported in multiple batches.
 */
public record Pillar3ProductImportRequest(
        @NotEmpty @Size(max = 1000) List<Pillar3ProductRequest> products
) {}
