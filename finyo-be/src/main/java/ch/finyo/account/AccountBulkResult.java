package ch.finyo.account;

import java.util.List;

/**
 * Outcome of a bulk account import. {@code errors} holds one
 * "row N: &lt;message&gt;" entry per failed row (1-based index) and is
 * always present — empty when every row succeeded.
 */
public record AccountBulkResult(
        int created,
        int updated,
        int failed,
        List<String> errors
) {}
