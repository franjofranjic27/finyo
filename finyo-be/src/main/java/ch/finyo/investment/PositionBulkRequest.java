package ch.finyo.investment;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Bulk import of positions (e.g. from a CSV parsed in the frontend).
 * Rows are intentionally NOT cascade-validated with Bean Validation:
 * the service validates each row and collects per-row errors instead of
 * rejecting the whole request.
 *
 * The size cap is not cosmetic. Every row now costs up to two calls to external
 * providers, so an unbounded list turns one HTTP request into thousands of outbound
 * ones — against sources whose rate limits we are expected to respect. 500 rows is far
 * beyond any real portfolio and still bounded.
 */
public record PositionBulkRequest(
        @NotEmpty @Size(max = MAX_ROWS) List<PositionRequest> positions
) {
    public static final int MAX_ROWS = 500;
}
