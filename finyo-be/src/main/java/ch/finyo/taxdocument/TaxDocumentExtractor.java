package ch.finyo.taxdocument;

/**
 * Strategy for extracting the relevant field values from the text layer of
 * one tax document type. Implementations are stateless Spring components.
 */
public interface TaxDocumentExtractor {

    TaxDocumentType supports();

    TaxDocumentExtractionResponse<?> extract(String text);
}
