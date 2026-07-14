package ch.finyo.taxdocument;

import ch.finyo.BaseIntegrationTest;
import ch.finyo.tax.TaxYear;
import ch.finyo.tax.TaxYearRepository;
import ch.finyo.tax.TaxYearStatus;
import org.assertj.core.api.AbstractBigDecimalAssert;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.InstanceOfAssertFactory;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for the cloud ingestion pipeline: delta enumeration →
 * download → classify/extract → auto-apply or review inbox.
 *
 * <p>Microsoft Graph is replaced by {@link StubRemoteDrive}: the real
 * {@code GraphRemoteDrive} is {@code @ConditionalOnProperty(finyo.graph.enabled)}
 * and therefore absent in the test profile, so {@code DocumentSyncService}'s
 * {@code Optional<RemoteDrive>} is satisfied by the stub instead. Everything
 * behind the drive — PDFBox, the classifier, the extractors, the folder
 * conventions and {@code TaxYearService} — is the real thing, running against a
 * real PostgreSQL container.
 *
 * <p>The PDFs are rendered from the same anonymized fixtures the extractor unit
 * tests use, so a document that "says 2025" really says it in its text layer.
 */
@Import(DocumentInboxIT.StubDriveConfig.class)
class DocumentInboxIT extends BaseIntegrationTest {

    private static final String SALARY_FIXTURE = "salary-certificate.txt";
    /** The fixture's period line reads "D 2025 E 01.01.2025 31.12.2025". */
    private static final int FIXTURE_TAX_YEAR = 2025;
    /** Ziffer 8 "Bruttolohn total" in the fixture. */
    private static final BigDecimal FIXTURE_GROSS_SALARY = new BigDecimal("84000");
    private static final BigDecimal PRE_EXISTING_INCOME = new BigDecimal("99999");

    private static final String ROOT_FOLDER = "/Steuern";
    private static final String PATH_2025 = "/Steuern/STE-2025/Lohnausweise/lohnausweis.pdf";
    private static final String PATH_2024 = "/Steuern/STE-2024/Lohnausweise/lohnausweis.pdf";
    private static final String ITEM_ID = "item-1";
    private static final String CTAG = "ctag-1";

    private static final String APPLY_ALL_FIELDS_2025 = """
            {"year":2025,"fieldNames":[]}""";

    @TestConfiguration
    static class StubDriveConfig {

        @Bean
        StubRemoteDrive stubRemoteDrive() {
            return new StubRemoteDrive();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentSourceRepository documentSourceRepository;

    @Autowired
    private TaxYearRepository taxYearRepository;

    @Autowired
    private StubRemoteDrive drive;

    @BeforeEach
    void reset() {
        documentRepository.deleteAll();
        documentSourceRepository.deleteAll();
        taxYearRepository.deleteAll();
        drive.reset();
        givenSource(TEST_USER_ID);
    }

    @Nested
    class AutomatedApply {

        @Test
        void writesTheExtractedSalaryIntoTheMatchingTaxYearWithoutAskingTheUser() throws Exception {
            drive.publish(pdfIn(PATH_2025), TestPdfFactory.pdfFromFixture(SALARY_FIXTURE));

            sync().andExpect(jsonPath("$.autoApplied", is(1)))
                    .andExpect(jsonPath("$.needsReview", is(0)))
                    .andExpect(jsonPath("$.failed", is(0)));

            assertThat(onlyDocument()).satisfies(document -> {
                assertThat(document.getStatus()).isEqualTo(DocumentStatus.AUTO_APPLIED);
                assertThat(document.getDetectedType()).isEqualTo(TaxDocumentType.SALARY_CERTIFICATE);
                assertThat(document.getDetectedTaxYear()).isEqualTo(FIXTURE_TAX_YEAR);
                assertThat(document.getFolderTaxYear()).isEqualTo(FIXTURE_TAX_YEAR);
                assertThat(document.getFailureReason()).isNull();
            });
            assertThat(taxYear(2025)).get()
                    .extracting(TaxYear::getGrossEmploymentIncome, BIG_DECIMAL)
                    .isEqualByComparingTo(FIXTURE_GROSS_SALARY);
        }

        @Test
        void exposesTheDocumentInTheInboxOfItsOwner() throws Exception {
            drive.publish(pdfIn(PATH_2025), TestPdfFactory.pdfFromFixture(SALARY_FIXTURE));
            sync();

            mockMvc.perform(get("/api/v1/documents").with(asUser()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].filename", is("lohnausweis.pdf")))
                    .andExpect(jsonPath("$[0].sourcePath", is(PATH_2025)))
                    .andExpect(jsonPath("$[0].status", is("AUTO_APPLIED")))
                    .andExpect(jsonPath("$[0].detectedType", is("SALARY_CERTIFICATE")))
                    .andExpect(jsonPath("$[0].folderType", is("SALARY_CERTIFICATE")))
                    .andExpect(jsonPath("$[0].confidence", notNullValue()))
                    .andExpect(jsonPath("$[0].deletedInDrive", is(false)))
                    .andExpect(jsonPath("$[0].extractedFields[0].targetTaxYearField", is("grossEmploymentIncome")))
                    .andExpect(jsonPath("$[0].extractedFields[0].value", is(84000)));
        }
    }

    @Nested
    class Guardrails {

        /**
         * The worst failure mode of this feature: a document whose text says 2025
         * filed under STE-2024 must not write into <em>either</em> year. Writing it
         * into the folder's year would silently corrupt a tax return; writing it
         * into the document's year would ignore the only signal the user maintains
         * deliberately. So nothing is written at all and the user decides.
         */
        @Test
        void neverTouchesAnyTaxYearWhenTheFolderYearContradictsTheDocumentYear() throws Exception {
            drive.publish(pdfIn(PATH_2024), TestPdfFactory.pdfFromFixture(SALARY_FIXTURE));

            sync().andExpect(jsonPath("$.autoApplied", is(0)))
                    .andExpect(jsonPath("$.needsReview", is(1)));

            assertThat(onlyDocument()).satisfies(document -> {
                assertThat(document.getStatus()).isEqualTo(DocumentStatus.NEEDS_REVIEW);
                assertThat(document.getDetectedTaxYear()).isEqualTo(2025);
                assertThat(document.getFolderTaxYear()).isEqualTo(2024);
            });
            assertThat(taxYear(2024)).isEmpty();
            assertThat(taxYear(2025)).isEmpty();
        }

        /** An existing, differing value is the user's — the automated path may fill blanks, never overwrite. */
        @Test
        void leavesAnExistingDifferentValueUntouchedAndAsksForReview() throws Exception {
            givenTaxYearWithIncome(2025, PRE_EXISTING_INCOME);
            drive.publish(pdfIn(PATH_2025), TestPdfFactory.pdfFromFixture(SALARY_FIXTURE));

            sync().andExpect(jsonPath("$.autoApplied", is(0)))
                    .andExpect(jsonPath("$.needsReview", is(1)));

            assertThat(onlyDocument().getStatus()).isEqualTo(DocumentStatus.NEEDS_REVIEW);
            assertThat(taxYear(2025)).get()
                    .extracting(TaxYear::getGrossEmploymentIncome, BIG_DECIMAL)
                    .isEqualByComparingTo(PRE_EXISTING_INCOME);
        }

        @Test
        void failsAScanWithoutATextLayerAndCreatesNoTaxYear() throws Exception {
            drive.publish(scanIn("/Steuern/STE-2025/Lohnausweise/scan.pdf"), TestPdfFactory.blankPagePdf());

            sync().andExpect(jsonPath("$.failed", is(1)));

            assertThat(onlyDocument()).satisfies(document -> {
                assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
                assertThat(document.getFailureReason()).contains("scan");
                assertThat(document.getExtractedFields()).isNullOrEmpty();
            });
            assertThat(taxYear(2025)).isEmpty();
        }
    }

    @Nested
    class IncrementalSync {

        /** The cTag is the only re-processing trigger: same content, no download, no re-apply. */
        @Test
        void doesNotDownloadTheSameFileTwiceWhenItsCtagIsUnchanged() throws Exception {
            drive.publish(pdfIn(PATH_2025), TestPdfFactory.pdfFromFixture(SALARY_FIXTURE));

            sync();
            sync().andExpect(jsonPath("$.autoApplied", is(0)))
                    .andExpect(jsonPath("$.needsReview", is(0)))
                    .andExpect(jsonPath("$.failed", is(0)));

            assertThat(drive.downloadCount()).isEqualTo(1);
            assertThat(onlyDocument().getStatus()).isEqualTo(DocumentStatus.AUTO_APPLIED);
        }

        /**
         * A file deleted in the drive is the audit trail for a number that is
         * already in a tax return — it is soft-deleted, and the applied value is
         * never rolled back.
         */
        @Test
        void keepsAnAlreadyAppliedDocumentAndItsValueWhenTheFileIsDeletedInTheDrive() throws Exception {
            drive.publish(pdfIn(PATH_2025), TestPdfFactory.pdfFromFixture(SALARY_FIXTURE));
            sync();

            drive.publishDeleted(ITEM_ID);
            sync();

            assertThat(onlyDocument()).satisfies(document -> {
                assertThat(document.getStatus()).isEqualTo(DocumentStatus.AUTO_APPLIED);
                assertThat(document.getSourceDeletedAt()).isNotNull();
            });
            assertThat(taxYear(2025)).get()
                    .extracting(TaxYear::getGrossEmploymentIncome, BIG_DECIMAL)
                    .isEqualByComparingTo(FIXTURE_GROSS_SALARY);

            mockMvc.perform(get("/api/v1/documents").with(asUser()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].deletedInDrive", is(true)))
                    .andExpect(jsonPath("$[0].status", is("AUTO_APPLIED")));
        }
    }

    @Nested
    class InboxActions {

        @Test
        void manualApplyOverwritesTheExistingValueTheUserHasSeen() throws Exception {
            givenTaxYearWithIncome(2025, PRE_EXISTING_INCOME);
            drive.publish(pdfIn(PATH_2025), TestPdfFactory.pdfFromFixture(SALARY_FIXTURE));
            sync();
            UUID documentId = onlyDocument().getId();

            mockMvc.perform(post("/api/v1/documents/{id}/apply", documentId).with(asUser())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(APPLY_ALL_FIELDS_2025))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("APPLIED")));

            assertThat(onlyDocument().getStatus()).isEqualTo(DocumentStatus.APPLIED);
            assertThat(taxYear(2025)).get()
                    .extracting(TaxYear::getGrossEmploymentIncome, BIG_DECIMAL)
                    .isEqualByComparingTo(FIXTURE_GROSS_SALARY);
        }

        @Test
        void dismissMarksTheDocumentAsDiscarded() throws Exception {
            givenTaxYearWithIncome(2025, PRE_EXISTING_INCOME);
            drive.publish(pdfIn(PATH_2025), TestPdfFactory.pdfFromFixture(SALARY_FIXTURE));
            sync();
            UUID documentId = onlyDocument().getId();

            mockMvc.perform(post("/api/v1/documents/{id}/dismiss", documentId).with(asUser()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("DISMISSED")));

            assertThat(onlyDocument().getStatus()).isEqualTo(DocumentStatus.DISMISSED);
            assertThat(taxYear(2025)).get()
                    .extracting(TaxYear::getGrossEmploymentIncome, BIG_DECIMAL)
                    .isEqualByComparingTo(PRE_EXISTING_INCOME);
        }
    }

    @Nested
    class CrossTenantIsolation {

        @Test
        void hidesForeignDocumentsFromTheInbox() throws Exception {
            drive.publish(pdfIn(PATH_2025), TestPdfFactory.pdfFromFixture(SALARY_FIXTURE));
            sync();

            mockMvc.perform(get("/api/v1/documents").with(asOtherUser()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", emptyIterable()));
        }

        @Test
        void answersWith404WhenAnotherUserTriesToApplyOrDismissAForeignDocument() throws Exception {
            drive.publish(pdfIn(PATH_2025), TestPdfFactory.pdfFromFixture(SALARY_FIXTURE));
            sync();
            UUID documentId = onlyDocument().getId();

            mockMvc.perform(post("/api/v1/documents/{id}/apply", documentId).with(asOtherUser())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(APPLY_ALL_FIELDS_2025))
                    .andExpect(status().isNotFound());
            mockMvc.perform(post("/api/v1/documents/{id}/dismiss", documentId).with(asOtherUser()))
                    .andExpect(status().isNotFound());

            assertThat(onlyDocument().getStatus()).isEqualTo(DocumentStatus.AUTO_APPLIED);
        }
    }

    @Nested
    class SourceAdministration {

        private static final String NEW_SOURCE = """
                {"driveId":"drive-2","rootFolderPath":"/Steuern","enabled":true}""";

        @Test
        void rejectsAPlainUserRegisteringACloudFolder() throws Exception {
            mockMvc.perform(post("/api/v1/admin/document-sources").with(asUser())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(NEW_SOURCE))
                    .andExpect(status().isForbidden());
        }

        /** The delta cursor is a credential-bearing URL: only its existence is exposed. */
        @Test
        void letsAnAdminRegisterACloudFolderWithoutEverExposingTheDeltaCursor() throws Exception {
            mockMvc.perform(post("/api/v1/admin/document-sources").with(asAdmin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(NEW_SOURCE))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.driveId", is("drive-2")))
                    .andExpect(jsonPath("$.userId", is(ADMIN_USER_ID)))
                    .andExpect(jsonPath("$.enabled", is(true)))
                    .andExpect(jsonPath("$.initialized", is(false)))
                    .andExpect(jsonPath("$.deltaLink").doesNotExist());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static final InstanceOfAssertFactory<BigDecimal, AbstractBigDecimalAssert<?>> BIG_DECIMAL =
            InstanceOfAssertFactories.BIG_DECIMAL;

    private ResultActions sync() throws Exception {
        return mockMvc.perform(post("/api/v1/documents/sync").with(asUser()))
                .andExpect(status().isOk());
    }

    private Document onlyDocument() {
        List<Document> documents = documentRepository.findByUserIdOrderByCreatedAtDesc(TEST_USER_ID);
        assertThat(documents).hasSize(1);
        return documents.getFirst();
    }

    private Optional<TaxYear> taxYear(int year) {
        return taxYearRepository.findByUserIdAndYear(TEST_USER_ID, year);
    }

    private void givenSource(String userId) {
        documentSourceRepository.save(DocumentSource.builder()
                .userId(userId)
                .driveId("drive-1")
                .rootFolderPath(ROOT_FOLDER)
                .enabled(true)
                .build());
    }

    private void givenTaxYearWithIncome(int year, BigDecimal grossEmploymentIncome) {
        taxYearRepository.save(TaxYear.builder()
                .userId(TEST_USER_ID)
                .year(year)
                .status(TaxYearStatus.OPEN)
                .grossEmploymentIncome(grossEmploymentIncome)
                .build());
    }

    private static RemoteDocument pdfIn(String path) {
        return remoteDocument(ITEM_ID, path);
    }

    private static RemoteDocument scanIn(String path) {
        return remoteDocument("item-scan", path);
    }

    private static RemoteDocument remoteDocument(String itemId, String path) {
        String filename = path.substring(path.lastIndexOf('/') + 1);
        return new RemoteDocument(itemId, filename, path, CTAG, 120_000L,
                "https://drive.invalid/content/" + itemId, false);
    }

    /**
     * In-memory {@link RemoteDrive}: returns whatever the test published as one
     * delta page and counts downloads, which is how the cTag short-circuit is
     * observed. No Graph client and no HTTP anywhere in this test.
     */
    static class StubRemoteDrive implements RemoteDrive {

        private final Map<String, RemoteDocument> items = new LinkedHashMap<>();
        private final Map<String, byte[]> contentByItemId = new LinkedHashMap<>();
        private int downloadCount;

        void reset() {
            items.clear();
            contentByItemId.clear();
            downloadCount = 0;
        }

        void publish(RemoteDocument item, byte[] content) {
            items.put(item.itemId(), item);
            contentByItemId.put(item.itemId(), content);
        }

        /** The drive reports a deletion: Graph sends the item id and a deleted facet only. */
        void publishDeleted(String itemId) {
            RemoteDocument item = items.get(itemId);
            items.put(itemId, new RemoteDocument(item.itemId(), item.filename(), item.path(),
                    item.ctag(), item.size(), null, true));
        }

        int downloadCount() {
            return downloadCount;
        }

        @Override
        public DeltaPage listChanges(String driveId, @Nullable String cursor) {
            return new DeltaPage(new ArrayList<>(items.values()), null, "delta-link-1");
        }

        @Override
        public byte[] download(RemoteDocument document) {
            downloadCount++;
            return contentByItemId.get(document.itemId());
        }
    }
}
