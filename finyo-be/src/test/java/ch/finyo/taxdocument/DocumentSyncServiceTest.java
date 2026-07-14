package ch.finyo.taxdocument;

import ch.finyo.common.DocumentBusyException;
import ch.finyo.common.DocumentProcessingException;
import ch.finyo.tax.FieldApplyResult;
import ch.finyo.tax.TaxYearService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Pins the auto-apply gate. The rule that matters: values only ever flow into a
 * tax year unattended when the folder and the document agree on both the type and
 * the year — never on the classifier's confidence, which is normalized per type
 * against unequal keyword maxima and is not comparable across types.
 */
@ExtendWith(MockitoExtension.class)
class DocumentSyncServiceTest {

    private static final UUID SOURCE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String USER_ID = "user-abc-123";

    @Mock
    private DocumentSourceRepository sourceRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private TaxDocumentService taxDocumentService;

    @Mock
    private TaxYearService taxYearService;

    @Mock
    private RemoteDrive remoteDrive;

    @Captor
    private ArgumentCaptor<Document> documentCaptor;

    private DocumentSyncService service;

    @BeforeEach
    void setUp() {
        FolderConventionRules rules = new FolderConventionRules(new FolderConventionProperties(
                "STE-(20\\d\\d)",
                Map.of("lohnausweise", TaxDocumentType.SALARY_CERTIFICATE,
                        "veranlagung", TaxDocumentType.ASSESSMENT)));
        service = new DocumentSyncService(sourceRepository, documentRepository, taxDocumentService,
                taxYearService, rules, Optional.of(remoteDrive));
        // Without a granted lease every sync would be skipped as "already running".
        lenient().when(sourceRepository.acquireLease(any(), any(), any())).thenReturn(1);
    }

    @Test
    void appliesAutomaticallyWhenFolderAndDocumentAgree() {
        givenDriveReturns(item("/Steuern/STE-2025/Lohnausweise", "lohnausweis.pdf"));
        givenAnalysis(TaxDocumentType.SALARY_CERTIFICATE, 2025);
        given(taxYearService.applyExtractedFields(anyString(), anyInt(), anyMap(), eq(false)))
                .willReturn(new FieldApplyResult(List.of("grossEmploymentIncome"), List.of()));

        DocumentSyncService.SyncResult result = service.syncAllEnabled();

        assertThat(result.autoApplied()).isEqualTo(1);
        verify(taxYearService).applyExtractedFields(eq(USER_ID), eq(2025), anyMap(), eq(false));
        assertThat(savedDocument().getStatus()).isEqualTo(DocumentStatus.AUTO_APPLIED);
    }

    /**
     * The worst failure mode this feature could have: a 2024 salary certificate
     * filed into the STE-2025 folder silently overwriting the 2025 income. The
     * year cross-check is what prevents it.
     */
    @Test
    void refusesToApplyWhenTheFolderYearContradictsTheDocumentYear() {
        givenDriveReturns(item("/Steuern/STE-2025/Lohnausweise", "lohnausweis.pdf"));
        givenAnalysis(TaxDocumentType.SALARY_CERTIFICATE, 2024);

        DocumentSyncService.SyncResult result = service.syncAllEnabled();

        assertThat(result.needsReview()).isEqualTo(1);
        verify(taxYearService, never()).applyExtractedFields(anyString(), anyInt(), anyMap(), any(Boolean.class));
        assertThat(savedDocument().getStatus()).isEqualTo(DocumentStatus.NEEDS_REVIEW);
    }

    @Test
    void refusesToApplyWhenTheFolderTypeContradictsTheDetectedType() {
        givenDriveReturns(item("/Steuern/STE-2025/Lohnausweise", "veranlagung.pdf"));
        givenAnalysis(TaxDocumentType.ASSESSMENT, 2025);

        service.syncAllEnabled();

        verify(taxYearService, never()).applyExtractedFields(anyString(), anyInt(), anyMap(), any(Boolean.class));
        assertThat(savedDocument().getStatus()).isEqualTo(DocumentStatus.NEEDS_REVIEW);
    }

    @Test
    void refusesToApplyWhenTheFolderCarriesNoHint() {
        givenDriveReturns(item("/Steuern/Sonstiges", "irgendwas.pdf"));
        givenAnalysis(TaxDocumentType.SALARY_CERTIFICATE, 2025);

        service.syncAllEnabled();

        verify(taxYearService, never()).applyExtractedFields(anyString(), anyInt(), anyMap(), any(Boolean.class));
        assertThat(savedDocument().getStatus()).isEqualTo(DocumentStatus.NEEDS_REVIEW);
    }

    @Test
    void sendsAnUnrecognizedDocumentToReviewInsteadOfFailingIt() {
        givenDriveReturns(item("/Steuern/STE-2025/Lohnausweise", "raetsel.pdf"));
        given(remoteDrive.download(any())).willReturn(new byte[]{1});
        given(taxDocumentService.analyze(any()))
                .willReturn(new DocumentAnalysis(
                        new ClassificationResponse(TaxDocumentType.UNKNOWN, 0.0), null));

        DocumentSyncService.SyncResult result = service.syncAllEnabled();

        assertThat(result.needsReview()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(savedDocument().getStatus()).isEqualTo(DocumentStatus.NEEDS_REVIEW);
    }

    /** A field already holding a different value is the user's call, not ours. */
    @Test
    void sendsToReviewWhenApplyingReportsAConflict() {
        givenDriveReturns(item("/Steuern/STE-2025/Lohnausweise", "lohnausweis.pdf"));
        givenAnalysis(TaxDocumentType.SALARY_CERTIFICATE, 2025);
        given(taxYearService.applyExtractedFields(anyString(), anyInt(), anyMap(), eq(false)))
                .willReturn(new FieldApplyResult(List.of(), List.of("grossEmploymentIncome")));

        DocumentSyncService.SyncResult result = service.syncAllEnabled();

        assertThat(result.needsReview()).isEqualTo(1);
        assertThat(savedDocument().getStatus()).isEqualTo(DocumentStatus.NEEDS_REVIEW);
    }

    @Test
    void marksAScanAsPermanentlyFailed() {
        givenDriveReturns(item("/Steuern/STE-2025/Lohnausweise", "scan.pdf"));
        given(remoteDrive.download(any())).willReturn(new byte[]{1});
        given(taxDocumentService.analyze(any()))
                .willThrow(new DocumentProcessingException("The PDF contains no extractable text"));

        DocumentSyncService.SyncResult result = service.syncAllEnabled();

        assertThat(result.failed()).isEqualTo(1);
        Document saved = savedDocument();
        assertThat(saved.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(saved.getFailureReason()).contains("no extractable text");
    }

    /**
     * Busy parse slots are transient. Marking the document FAILED here would lose a
     * perfectly good document just because interactive uploads saturated the two
     * parse slots at that moment.
     */
    @Test
    void keepsADocumentPendingWhenTheParserIsBusy() {
        givenDriveReturns(item("/Steuern/STE-2025/Lohnausweise", "lohnausweis.pdf"));
        given(remoteDrive.download(any())).willReturn(new byte[]{1});
        given(taxDocumentService.analyze(any())).willThrow(new DocumentBusyException("Server is busy"));

        DocumentSyncService.SyncResult result = service.syncAllEnabled();

        assertThat(result.failed()).isZero();
        Document saved = savedDocument();
        assertThat(saved.getStatus()).isEqualTo(DocumentStatus.DISCOVERED);
        assertThat(saved.getAttemptCount()).isEqualTo(1);
        assertThat(saved.getFailureReason()).isNull();
    }

    @Test
    void keepsADocumentPendingWhenTheDriveIsUnreachable() {
        givenDriveReturns(item("/Steuern/STE-2025/Lohnausweise", "lohnausweis.pdf"));
        given(remoteDrive.download(any())).willThrow(new RemoteDriveException("The drive could not be reached", null));

        DocumentSyncService.SyncResult result = service.syncAllEnabled();

        assertThat(result.failed()).isZero();
        assertThat(savedDocument().getStatus()).isEqualTo(DocumentStatus.DISCOVERED);
    }

    @Test
    void givesUpAfterRepeatedTransientFailures() {
        RemoteDocument remote = item("/Steuern/STE-2025/Lohnausweise", "lohnausweis.pdf");
        givenDriveReturns(remote);
        given(documentRepository.findBySourceIdAndSourceItemId(SOURCE_ID, remote.itemId()))
                .willReturn(Optional.of(Document.builder()
                        .userId(USER_ID)
                        .sourceId(SOURCE_ID)
                        .sourceItemId(remote.itemId())
                        .sourcePath(remote.path())
                        .sourceCtag("older-ctag")
                        .filename(remote.filename())
                        .status(DocumentStatus.DISCOVERED)
                        .attemptCount(4)
                        .build()));
        given(remoteDrive.download(any())).willThrow(new RemoteDriveException("The drive could not be reached", null));

        DocumentSyncService.SyncResult result = service.syncAllEnabled();

        assertThat(result.failed()).isEqualTo(1);
        Document saved = savedDocument();
        assertThat(saved.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(saved.getFailureReason()).contains("Too many failed attempts");
    }

    /** Unchanged content must not be downloaded, let alone parsed, on every run. */
    @Test
    void skipsDocumentsWhoseContentDidNotChange() {
        RemoteDocument remote = item("/Steuern/STE-2025/Lohnausweise", "lohnausweis.pdf");
        givenDriveReturns(remote);
        given(documentRepository.findBySourceIdAndSourceItemId(SOURCE_ID, remote.itemId()))
                .willReturn(Optional.of(Document.builder()
                        .userId(USER_ID)
                        .sourceId(SOURCE_ID)
                        .sourceItemId(remote.itemId())
                        .sourcePath(remote.path())
                        .sourceCtag(remote.ctag())
                        .filename(remote.filename())
                        .status(DocumentStatus.AUTO_APPLIED)
                        .build()));

        service.syncAllEnabled();

        verify(remoteDrive, never()).download(any());
        verify(taxDocumentService, never()).analyze(any());
        verify(documentRepository, never()).save(any());
    }

    /**
     * A moved file keeps its values: re-applying because it changed folders would
     * rewrite a tax year behind the user's back.
     */
    @Test
    void updatesOnlyThePathWhenAnAppliedDocumentIsMoved() {
        RemoteDocument remote = item("/Steuern/Archiv/STE-2025/Lohnausweise", "lohnausweis.pdf");
        givenDriveReturns(remote);
        given(documentRepository.findBySourceIdAndSourceItemId(SOURCE_ID, remote.itemId()))
                .willReturn(Optional.of(Document.builder()
                        .userId(USER_ID)
                        .sourceId(SOURCE_ID)
                        .sourceItemId(remote.itemId())
                        .sourcePath("/Steuern/STE-2025/Lohnausweise")
                        .sourceCtag(remote.ctag())
                        .filename(remote.filename())
                        .status(DocumentStatus.AUTO_APPLIED)
                        .build()));

        service.syncAllEnabled();

        verify(taxYearService, never()).applyExtractedFields(anyString(), anyInt(), anyMap(), any(Boolean.class));
        Document saved = savedDocument();
        assertThat(saved.getSourcePath()).isEqualTo("/Steuern/Archiv/STE-2025/Lohnausweise");
        assertThat(saved.getStatus()).isEqualTo(DocumentStatus.AUTO_APPLIED);
    }

    /** Changed content is the escape hatch: re-uploading an OCR'd file revives a FAILED document. */
    @Test
    void reprocessesADocumentWhoseContentChanged() {
        RemoteDocument remote = item("/Steuern/STE-2025/Lohnausweise", "lohnausweis.pdf");
        givenDriveReturns(remote);
        given(documentRepository.findBySourceIdAndSourceItemId(SOURCE_ID, remote.itemId()))
                .willReturn(Optional.of(Document.builder()
                        .userId(USER_ID)
                        .sourceId(SOURCE_ID)
                        .sourceItemId(remote.itemId())
                        .sourcePath(remote.path())
                        .sourceCtag("stale-ctag")
                        .filename(remote.filename())
                        .status(DocumentStatus.FAILED)
                        .failureReason("Scanned documents are not supported")
                        .build()));
        givenAnalysis(TaxDocumentType.SALARY_CERTIFICATE, 2025);
        given(taxYearService.applyExtractedFields(anyString(), anyInt(), anyMap(), eq(false)))
                .willReturn(new FieldApplyResult(List.of("grossEmploymentIncome"), List.of()));

        service.syncAllEnabled();

        Document saved = savedDocument();
        assertThat(saved.getStatus()).isEqualTo(DocumentStatus.AUTO_APPLIED);
        assertThat(saved.getFailureReason()).isNull();
    }

    @Test
    void ignoresFilesOutsideTheConfiguredFolderAndNonPdfs() {
        given(sourceRepository.findByEnabledTrue()).willReturn(List.of(source()));
        given(remoteDrive.listChanges(anyString(), any())).willReturn(new DeltaPage(
                List.of(item("/Privat/Fotos", "urlaub.pdf"), item("/Steuern/STE-2025", "notizen.txt")),
                null,
                "delta-1"));

        service.syncAllEnabled();

        verify(remoteDrive, never()).download(any());
        verify(documentRepository, never()).save(any());
    }

    /** Deleting the cloud file is not a reason to undo a number already in the tax return. */
    @Test
    void softDeletesWithoutRollingBackAppliedValues() {
        RemoteDocument deleted = new RemoteDocument(
                "item-1", "lohnausweis.pdf", "/Steuern/STE-2025/Lohnausweise", "ctag-1", 100, null, true);
        given(sourceRepository.findByEnabledTrue()).willReturn(List.of(source()));
        given(remoteDrive.listChanges(anyString(), any()))
                .willReturn(new DeltaPage(List.of(deleted), null, "delta-1"));
        given(documentRepository.findBySourceIdAndSourceItemId(SOURCE_ID, "item-1"))
                .willReturn(Optional.of(Document.builder()
                        .userId(USER_ID)
                        .sourceId(SOURCE_ID)
                        .sourceItemId("item-1")
                        .sourcePath("/Steuern/STE-2025/Lohnausweise")
                        .filename("lohnausweis.pdf")
                        .status(DocumentStatus.AUTO_APPLIED)
                        .build()));

        service.syncAllEnabled();

        Document saved = savedDocument();
        assertThat(saved.getSourceDeletedAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(DocumentStatus.AUTO_APPLIED);
        verify(taxYearService, never()).applyExtractedFields(anyString(), anyInt(), anyMap(), any(Boolean.class));
    }

    /**
     * The cursor is only trustworthy once every page was processed — it is handed
     * out on the last page, and a nextLink must never be stored as one.
     */
    @Test
    void storesTheCursorOnlyAfterTheLastPage() {
        given(sourceRepository.findByEnabledTrue()).willReturn(List.of(source()));
        given(remoteDrive.listChanges(anyString(), any()))
                .willReturn(new DeltaPage(List.of(), "next-page", null))
                .willReturn(new DeltaPage(List.of(), null, "final-cursor"));

        service.syncAllEnabled();

        verify(remoteDrive, times(2)).listChanges(anyString(), any());
        ArgumentCaptor<DocumentSource> sourceCaptor = ArgumentCaptor.forClass(DocumentSource.class);
        verify(sourceRepository).save(sourceCaptor.capture());
        assertThat(sourceCaptor.getValue().getDeltaLink()).isEqualTo("final-cursor");
    }

    @Test
    void recoversFromAnExpiredCursorByEnumeratingFromScratch() {
        given(sourceRepository.findByEnabledTrue()).willReturn(List.of(source("stale-cursor")));
        given(remoteDrive.listChanges("drive-1", "stale-cursor"))
                .willThrow(new DeltaResyncRequiredException("Delta cursor expired"));
        given(remoteDrive.listChanges("drive-1", null))
                .willReturn(new DeltaPage(List.of(), null, "fresh-cursor"));

        service.syncAllEnabled();

        ArgumentCaptor<DocumentSource> sourceCaptor = ArgumentCaptor.forClass(DocumentSource.class);
        verify(sourceRepository).save(sourceCaptor.capture());
        assertThat(sourceCaptor.getValue().getDeltaLink()).isEqualTo("fresh-cursor");
    }

    // -------------------------------------------------------------------------

    private void givenDriveReturns(RemoteDocument item) {
        given(sourceRepository.findByEnabledTrue()).willReturn(List.of(source()));
        given(remoteDrive.listChanges(anyString(), any()))
                .willReturn(new DeltaPage(List.of(item), null, "delta-1"));
    }

    private void givenAnalysis(TaxDocumentType type, int taxYear) {
        given(remoteDrive.download(any())).willReturn(new byte[]{1});
        given(taxDocumentService.analyze(any())).willReturn(new DocumentAnalysis(
                new ClassificationResponse(type, 0.8),
                new TaxDocumentExtractionResponse<>(
                        type,
                        taxYear,
                        Map.of(),
                        List.of(new ExtractedField("grossSalary", "Bruttolohn",
                                new BigDecimal("52592.00"), "grossEmploymentIncome")))));
    }

    private Document savedDocument() {
        verify(documentRepository).save(documentCaptor.capture());
        return documentCaptor.getValue();
    }

    private static DocumentSource source() {
        return source(null);
    }

    private static DocumentSource source(String deltaLink) {
        return DocumentSource.builder()
                .id(SOURCE_ID)
                .userId(USER_ID)
                .driveId("drive-1")
                .rootFolderPath("/Steuern")
                .deltaLink(deltaLink)
                .enabled(true)
                .build();
    }

    private static RemoteDocument item(String path, String filename) {
        return new RemoteDocument(
                "item-1", filename, path, "ctag-1", 100, "https://storage.example/blob", false);
    }
}
