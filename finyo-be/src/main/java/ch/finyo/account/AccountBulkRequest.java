package ch.finyo.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Bulk import payload for accounts. Items are cascade-validated at the
 * controller boundary, so a single invalid item rejects the whole request
 * with 400 — consistent with the single-item endpoints. The size cap bounds
 * memory usage per request; larger imports are split into multiple batches.
 */
public record AccountBulkRequest(
        @NotNull @Size(min = 1, max = 200) List<@Valid AccountRequest> items
) {}
