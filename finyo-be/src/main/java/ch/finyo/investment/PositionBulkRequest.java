package ch.finyo.investment;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Bulk import of positions (e.g. from a CSV parsed in the frontend).
 * Rows are intentionally NOT cascade-validated with Bean Validation:
 * the service validates each row and collects per-row errors instead of
 * rejecting the whole request.
 */
public record PositionBulkRequest(
        @NotEmpty List<PositionRequest> positions
) {}
