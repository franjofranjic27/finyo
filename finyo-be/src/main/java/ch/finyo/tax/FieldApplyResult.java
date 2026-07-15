package ch.finyo.tax;

import java.util.List;

/**
 * Outcome of applying extracted document fields to a tax year.
 *
 * @param applied target field names that now hold the extracted value (including
 *                fields that already held exactly that value)
 * @param skipped target field names left untouched because they already held a
 *                <em>different</em> value and overwriting was not requested —
 *                the caller turns this into a review prompt
 */
public record FieldApplyResult(List<String> applied, List<String> skipped) {

    public boolean hasConflicts() {
        return !skipped.isEmpty();
    }
}
