package ch.finyo.taxdocument;

import org.jspecify.annotations.Nullable;

/**
 * Result of analysing a document without knowing its type up front.
 *
 * <p>Unlike the interactive endpoints, batch ingestion has no expected type to
 * check against: an unrecognized document is a normal outcome (it lands in the
 * review inbox), not an error. It also needs the confidence score, which the
 * extraction response alone does not carry.
 *
 * @param classification detected type and confidence
 * @param extraction     extracted fields, or {@code null} when the type is
 *                       {@link TaxDocumentType#UNKNOWN} and no extractor applies
 */
public record DocumentAnalysis(
        ClassificationResponse classification,
        @Nullable TaxDocumentExtractionResponse<?> extraction) {
}
