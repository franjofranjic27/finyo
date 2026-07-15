package ch.finyo.taxdocument;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A tax document discovered in a cloud folder. The file itself is never copied
 * into finyo — the drive stays the system of record and the content is fetched
 * on demand.
 */
@Entity
@Table(name = "document")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "source_item_id", nullable = false)
    private String sourceItemId;

    @Column(name = "source_path", nullable = false, length = 1024)
    private String sourcePath;

    /** Changes only when the file content changes — the trigger for re-processing. */
    @Column(name = "source_ctag")
    private @Nullable String sourceCtag;

    @Column(name = "source_deleted_at")
    private @Nullable OffsetDateTime sourceDeletedAt;

    @Column(nullable = false)
    private String filename;

    @Column
    private @Nullable Long size;

    @Enumerated(EnumType.STRING)
    @Column(name = "detected_type", length = 30)
    private @Nullable TaxDocumentType detectedType;

    /** Shown to the user only. Never a gate for auto-apply: the classifier's score is
     *  normalized per type against unequal keyword maxima, so it is not comparable
     *  across document types. */
    @Column(precision = 5, scale = 4)
    private @Nullable BigDecimal confidence;

    /** Tax year read from the document text. */
    @Column(name = "detected_tax_year")
    private @Nullable Integer detectedTaxYear;

    /** Tax year read from the folder path (e.g. STE-2025). */
    @Column(name = "folder_tax_year")
    private @Nullable Integer folderTaxYear;

    /** Document type implied by the folder name (e.g. Lohnausweise). */
    @Enumerated(EnumType.STRING)
    @Column(name = "folder_type", length = 30)
    private @Nullable TaxDocumentType folderType;

    @Convert(converter = ExtractedFieldsConverter.class)
    @Column(name = "extracted_fields", columnDefinition = "TEXT")
    private @Nullable List<ExtractedField> extractedFields;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private @Nullable String failureReason;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
