package ch.finyo.taxdocument;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Envelope for a document extraction: the typed field values plus the subset
 * of fields that map onto {@code TaxYear} inputs ({@code taxYearMapping}
 * contains only fields that were actually extracted and have a target).
 */
public record TaxDocumentExtractionResponse<T>(
        TaxDocumentType type,
        @Nullable Integer taxYear,
        T fields,
        List<ExtractedField> taxYearMapping
) {}
