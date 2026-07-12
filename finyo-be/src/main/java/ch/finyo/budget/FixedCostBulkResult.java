package ch.finyo.budget;

import java.util.List;

/**
 * Outcome of a bulk fixed-cost import. {@code errors} holds one
 * "row N: &lt;message&gt;" entry per failed row (1-based index) and is
 * always present — empty when every row succeeded.
 */
public record FixedCostBulkResult(
        int created,
        int updated,
        int failed,
        List<String> errors
) {}
