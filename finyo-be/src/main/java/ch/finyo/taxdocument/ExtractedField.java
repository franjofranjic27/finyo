package ch.finyo.taxdocument;

import java.math.BigDecimal;

/**
 * A successfully extracted amount together with the {@code TaxYear} field it
 * maps to (e.g. {@code grossSalary} → {@code grossEmploymentIncome}). Used by
 * the frontend to prefill the tax year form; nothing is persisted server-side.
 */
public record ExtractedField(
        String fieldName,
        String label,
        BigDecimal value,
        String targetTaxYearField
) {}
