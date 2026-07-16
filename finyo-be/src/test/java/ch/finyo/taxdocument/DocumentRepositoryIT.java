package ch.finyo.taxdocument;

import ch.finyo.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentSourceRepository documentSourceRepository;

    @BeforeEach
    void resetTables() {
        documentRepository.deleteAll();
        documentSourceRepository.deleteAll();
    }

    @Test
    void persistsAndReadsBackExtractedFieldsAsJson() {
        DocumentSource source = givenSource(TEST_USER_ID);
        Document saved = documentRepository.save(document(source, "item-1")
                .detectedType(TaxDocumentType.SALARY_CERTIFICATE)
                .confidence(new BigDecimal("0.8000"))
                .detectedTaxYear(2025)
                .extractedFields(List.of(new ExtractedField(
                        "grossSalary", "Bruttolohn", new BigDecimal("52592.00"), "grossEmploymentIncome")))
                .status(DocumentStatus.NEEDS_REVIEW)
                .build());

        Document reloaded = documentRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getExtractedFields()).singleElement().satisfies(field -> {
            assertThat(field.fieldName()).isEqualTo("grossSalary");
            assertThat(field.value()).isEqualByComparingTo("52592.00");
            assertThat(field.targetTaxYearField()).isEqualTo("grossEmploymentIncome");
        });
        assertThat(reloaded.getDetectedType()).isEqualTo(TaxDocumentType.SALARY_CERTIFICATE);
        assertThat(reloaded.getStatus()).isEqualTo(DocumentStatus.NEEDS_REVIEW);
    }

    /**
     * The natural key that makes a delta re-sync idempotent: re-enumerating the
     * same drive item must not create a second row.
     */
    @Test
    void rejectsTheSameItemTwiceWithinOneSource() {
        DocumentSource source = givenSource(TEST_USER_ID);
        documentRepository.save(document(source, "item-1").status(DocumentStatus.DISCOVERED).build());

        assertThatThrownBy(() -> documentRepository.saveAndFlush(
                document(source, "item-1").status(DocumentStatus.DISCOVERED).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Item IDs are unique per drive only, so the same ID in another source is a different document. */
    @Test
    void allowsTheSameItemIdInAnotherSource() {
        DocumentSource first = givenSource(TEST_USER_ID);
        DocumentSource second = documentSourceRepository.save(DocumentSource.builder()
                .userId(TEST_USER_ID)
                .driveId("drive-2")
                .rootFolderPath("/Steuern")
                .enabled(true)
                .build());

        documentRepository.save(document(first, "item-1").status(DocumentStatus.DISCOVERED).build());
        documentRepository.saveAndFlush(document(second, "item-1").status(DocumentStatus.DISCOVERED).build());

        assertThat(documentRepository.findBySourceIdAndSourceItemId(second.getId(), "item-1")).isPresent();
    }

    @Test
    void doesNotLeakDocumentsAcrossUsers() {
        DocumentSource source = givenSource(TEST_USER_ID);
        Document mine = documentRepository.save(document(source, "item-1").status(DocumentStatus.NEEDS_REVIEW).build());

        assertThat(documentRepository.findByUserIdOrderByCreatedAtDesc(OTHER_USER_ID)).isEmpty();
        assertThat(documentRepository.findByIdAndUserId(mine.getId(), OTHER_USER_ID)).isEmpty();
        assertThat(documentRepository.findByIdAndUserId(mine.getId(), TEST_USER_ID)).isPresent();
    }

    @Test
    void findsOnlyEnabledSourcesForTheSyncJob() {
        givenSource(TEST_USER_ID);
        documentSourceRepository.save(DocumentSource.builder()
                .userId(OTHER_USER_ID)
                .driveId("drive-disabled")
                .rootFolderPath("/Steuern")
                .enabled(false)
                .build());

        List<DocumentSource> enabled = documentSourceRepository.findByEnabledTrue();

        assertThat(enabled).extracting(DocumentSource::getDriveId).containsExactly("drive-1");
    }

    @Test
    void storesNullWhenThereAreNoExtractedFields() {
        DocumentSource source = givenSource(TEST_USER_ID);
        Document saved = documentRepository.save(document(source, "item-1")
                .status(DocumentStatus.FAILED)
                .failureReason("Scanned documents are not supported")
                .build());

        Optional<Document> reloaded = documentRepository.findById(saved.getId());

        assertThat(reloaded).get().satisfies(doc -> {
            assertThat(doc.getExtractedFields()).isNull();
            assertThat(doc.getFailureReason()).isEqualTo("Scanned documents are not supported");
            assertThat(doc.getAttemptCount()).isZero();
        });
    }

    private DocumentSource givenSource(String userId) {
        return documentSourceRepository.save(DocumentSource.builder()
                .userId(userId)
                .driveId("drive-1")
                .rootFolderPath("/Steuern")
                .enabled(true)
                .build());
    }

    private Document.DocumentBuilder document(DocumentSource source, String itemId) {
        return Document.builder()
                .userId(source.getUserId())
                .sourceId(source.getId())
                .sourceItemId(itemId)
                .sourcePath("/Steuern/STE-2025/Lohnausweise/lohnausweis.pdf")
                .sourceCtag("ctag-1")
                .filename("lohnausweis.pdf")
                .size(120_000L);
    }
}
