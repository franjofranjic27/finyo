package ch.finyo.taxdocument;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A document as the inbox shows it.
 *
 * <p>Both the detected and the folder-derived year and type are exposed so the UI
 * can explain <em>why</em> a document was not applied automatically, instead of
 * just stating that it wasn't.
 */
public record DocumentResponse(
        UUID id,
        String filename,
        String sourcePath,
        DocumentStatus status,
        @Nullable TaxDocumentType detectedType,
        @Nullable TaxDocumentType folderType,
        @Nullable BigDecimal confidence,
        @Nullable Integer detectedTaxYear,
        @Nullable Integer folderTaxYear,
        List<ExtractedField> extractedFields,
        @Nullable String failureReason,
        boolean deletedInDrive,
        OffsetDateTime createdAt) {

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getFilename(),
                document.getSourcePath(),
                document.getStatus(),
                document.getDetectedType(),
                document.getFolderType(),
                document.getConfidence(),
                document.getDetectedTaxYear(),
                document.getFolderTaxYear(),
                document.getExtractedFields() == null ? List.of() : document.getExtractedFields(),
                document.getFailureReason(),
                document.getSourceDeletedAt() != null,
                document.getCreatedAt());
    }
}
