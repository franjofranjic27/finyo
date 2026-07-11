package ch.finyo.taxdocument;

public record ClassificationResponse(
        TaxDocumentType detectedType,
        double confidence
) {}
