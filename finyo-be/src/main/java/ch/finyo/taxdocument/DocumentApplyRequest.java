package ch.finyo.taxdocument;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

/**
 * @param year       the tax year to write into — explicit, because the user may
 *                   correct a wrongly detected year in the inbox
 * @param fieldNames the fields ticked in the UI; empty applies all of them
 */
public record DocumentApplyRequest(
        @NotNull @Min(2000) @Max(2100) Integer year,
        Set<String> fieldNames) {

    public Set<String> fieldNames() {
        return fieldNames == null ? Set.of() : fieldNames;
    }
}
