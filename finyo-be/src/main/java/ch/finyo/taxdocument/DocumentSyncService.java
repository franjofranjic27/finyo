package ch.finyo.taxdocument;

import ch.finyo.common.DocumentBusyException;
import ch.finyo.common.DocumentProcessingException;
import ch.finyo.tax.FieldApplyResult;
import ch.finyo.tax.TaxYearService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Pulls documents from the configured cloud folders and runs them through
 * classification and extraction.
 *
 * <p>The delta cursor is only stored once a run finished completely: Graph hands
 * out the cursor on the last page only, and a crash halfway through must lead to
 * the changes being seen again rather than silently skipped. Repeating them is
 * harmless — documents are keyed by (source, item) and applying the same value
 * twice is a no-op.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentSyncService {

    /** After this many transient failures a document is given up on. */
    private static final int MAX_ATTEMPTS = 5;

    /** A lease older than this belongs to a run that died; it may be taken over. */
    private static final Duration ABANDONED_LEASE_AFTER = Duration.ofMinutes(30);

    private final DocumentSourceRepository sourceRepository;
    private final DocumentRepository documentRepository;
    private final TaxDocumentService taxDocumentService;
    private final TaxYearService taxYearService;
    private final FolderConventionRules folderConventionRules;

    /** Absent unless finyo.graph.enabled=true; integration tests inject a stub. */
    private final Optional<RemoteDrive> remoteDrive;

    public SyncResult syncForUser(String userId) {
        List<DocumentSource> sources = sourceRepository.findByUserId(userId).stream()
                .filter(DocumentSource::isEnabled)
                .toList();
        return syncSources(sources);
    }

    public SyncResult syncAllEnabled() {
        return syncSources(sourceRepository.findByEnabledTrue());
    }

    private SyncResult syncSources(List<DocumentSource> sources) {
        if (sources.isEmpty()) {
            return SyncResult.empty();
        }
        RemoteDrive drive = remoteDrive.orElseThrow(() -> new IllegalStateException(
                "No remote drive configured — set finyo.graph.enabled=true and provide Graph credentials"));

        SyncResult total = SyncResult.empty();
        for (DocumentSource source : sources) {
            total = total.plus(syncSource(source, drive));
        }
        return total;
    }

    private SyncResult syncSource(DocumentSource source, RemoteDrive drive) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (sourceRepository.acquireLease(source.getId(), now, now.minus(ABANDONED_LEASE_AFTER)) == 0) {
            log.info("Source={} is already being synced, skipping this run", source.getId());
            return SyncResult.empty();
        }
        log.info("Syncing document source={} drive={} folder={}",
                source.getId(), source.getDriveId(), source.getRootFolderPath());
        try {
            return enumerate(source, drive, source.getDeltaLink());
        } catch (DeltaResyncRequiredException e) {
            log.info("Delta cursor for source={} expired, enumerating from scratch", source.getId());
            return enumerate(source, drive, null);
        } finally {
            sourceRepository.releaseLease(source.getId());
        }
    }

    private SyncResult enumerate(DocumentSource source, RemoteDrive drive, @Nullable String cursor) {
        SyncResult result = SyncResult.empty();
        String next = cursor;
        String finalCursor = null;
        do {
            DeltaPage page = drive.listChanges(source.getDriveId(), next);
            for (RemoteDocument item : page.items()) {
                result = result.plus(handle(source, drive, item));
            }
            next = page.nextLink();
            finalCursor = page.deltaLink();
        } while (next != null);

        // Only now, with every page processed, is the cursor safe to keep.
        sourceRepository.save(source.toBuilder()
                .deltaLink(finalCursor)
                .lastSyncAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
        log.info("Sync of source={} finished: {}", source.getId(), result);
        return result;
    }

    private SyncResult handle(DocumentSource source, RemoteDrive drive, RemoteDocument item) {
        Optional<Document> existing =
                documentRepository.findBySourceIdAndSourceItemId(source.getId(), item.itemId());

        if (item.deleted()) {
            existing.ifPresent(this::markSourceDeleted);
            return SyncResult.of(0, 0, 0);
        }
        if (!isPdf(item.filename()) || !isInsideRootFolder(source, item)) {
            return SyncResult.empty();
        }

        if (existing.isPresent()) {
            Document document = existing.get();
            boolean contentUnchanged = Objects.equals(document.getSourceCtag(), item.ctag());
            if (contentUnchanged) {
                // Nothing to re-read. A moved or renamed file only updates its path;
                // re-applying values because a file changed folders would rewrite a
                // tax year behind the user's back.
                refreshPathIfMoved(document, item);
                return SyncResult.of(0, 0, 0);
            }
        }

        return process(source, drive, item, existing.orElse(null));
    }

    private SyncResult process(DocumentSource source,
                               RemoteDrive drive,
                               RemoteDocument item,
                               @Nullable Document existing) {
        Document.DocumentBuilder builder = (existing == null ? Document.builder() : existing.toBuilder())
                .userId(source.getUserId())
                .sourceId(source.getId())
                .sourceItemId(item.itemId())
                .sourcePath(item.path())
                .sourceCtag(item.ctag())
                .sourceDeletedAt(null)
                .filename(item.filename())
                .size(item.size());

        int attempts = existing == null ? 0 : existing.getAttemptCount();
        try {
            byte[] content = drive.download(item);
            DocumentAnalysis analysis = taxDocumentService.analyze(content);
            return applyAnalysis(source, item, builder, analysis);
        } catch (DocumentBusyException | RestClientException e) {
            // Transient: the same bytes will parse on a later run. Only give up
            // after repeated failures, and never mark the document failed for a
            // busy parse slot or a flaky network.
            int attempt = attempts + 1;
            boolean exhausted = attempt >= MAX_ATTEMPTS;
            documentRepository.save(builder
                    .status(exhausted ? DocumentStatus.FAILED : DocumentStatus.DISCOVERED)
                    .failureReason(exhausted ? "Too many failed attempts: " + e.getMessage() : null)
                    .attemptCount(attempt)
                    .build());
            log.warn("Transient failure on document {} (attempt {}/{}): {}",
                    item.filename(), attempt, MAX_ATTEMPTS, e.getMessage());
            return exhausted ? SyncResult.of(0, 0, 1) : SyncResult.empty();
        } catch (DocumentProcessingException | IllegalArgumentException e) {
            // Permanent: a scan without OCR, an encrypted or corrupt file, or one
            // beyond the size cap. Retrying cannot help — but re-uploading an OCR'd
            // version changes the cTag and brings the document back through here.
            documentRepository.save(builder
                    .status(DocumentStatus.FAILED)
                    .failureReason(e.getMessage())
                    .attemptCount(attempts)
                    .build());
            log.info("Document {} cannot be processed: {}", item.filename(), e.getMessage());
            return SyncResult.of(0, 0, 1);
        }
    }

    private SyncResult applyAnalysis(DocumentSource source,
                                     RemoteDocument item,
                                     Document.DocumentBuilder builder,
                                     DocumentAnalysis analysis) {
        FolderConventionRules.FolderHint hint = folderConventionRules.hintFor(item.path());
        TaxDocumentType detectedType = analysis.classification().detectedType();
        TaxDocumentExtractionResponse<?> extraction = analysis.extraction();
        Integer detectedYear = extraction == null ? null : extraction.taxYear();
        List<ExtractedField> fields = extraction == null ? List.of() : extraction.taxYearMapping();

        builder.detectedType(detectedType)
                .confidence(BigDecimal.valueOf(analysis.classification().confidence()))
                .detectedTaxYear(detectedYear)
                .folderTaxYear(hint.taxYear())
                .folderType(hint.type())
                .extractedFields(fields)
                .attemptCount(0)
                .failureReason(null);

        if (!qualifiesForAutoApply(hint, detectedType, detectedYear, fields)) {
            documentRepository.save(builder.status(DocumentStatus.NEEDS_REVIEW).build());
            return SyncResult.of(0, 1, 0);
        }

        // Safe by construction: applyExtractedFields with overwrite=false fills
        // empty fields only and reports anything already holding a different value.
        FieldApplyResult applied = taxYearService.applyExtractedFields(
                source.getUserId(), detectedYear, toValueMap(fields), false);

        DocumentStatus status = applied.hasConflicts()
                ? DocumentStatus.NEEDS_REVIEW
                : DocumentStatus.AUTO_APPLIED;
        documentRepository.save(builder.status(status).build());
        return applied.hasConflicts() ? SyncResult.of(0, 1, 0) : SyncResult.of(1, 0, 0);
    }

    /**
     * The gate for writing into a tax year unattended. It rests on the folder, not
     * on the classifier's confidence: that score is normalized per type against
     * unequal keyword maxima and is therefore not comparable across types.
     *
     * <p>The year check is what stops the worst failure mode of this feature — a
     * 2024 salary certificate that ended up in the STE-2025 folder silently
     * overwriting the 2025 income.
     */
    private boolean qualifiesForAutoApply(FolderConventionRules.FolderHint hint,
                                          TaxDocumentType detectedType,
                                          @Nullable Integer detectedYear,
                                          List<ExtractedField> fields) {
        return !fields.isEmpty()
                && detectedType != TaxDocumentType.UNKNOWN
                && hint.type() == detectedType
                && detectedYear != null
                && detectedYear.equals(hint.taxYear());
    }

    private static Map<String, BigDecimal> toValueMap(List<ExtractedField> fields) {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        for (ExtractedField field : fields) {
            if (field.targetTaxYearField() != null && field.value() != null) {
                values.put(field.targetTaxYearField(), field.value());
            }
        }
        return values;
    }

    /**
     * A document that already fed a value into a tax year is the audit trail for
     * where that number came from, so it is never hard-deleted and an applied
     * value is never rolled back.
     */
    private void markSourceDeleted(Document document) {
        if (document.getSourceDeletedAt() != null) {
            return;
        }
        documentRepository.save(document.toBuilder().sourceDeletedAt(OffsetDateTime.now(ZoneOffset.UTC)).build());
        log.info("Document {} was deleted in the drive; keeping it as an audit trail", document.getFilename());
    }

    /**
     * A moved file keeps the values it already contributed — re-applying because it
     * changed folders would rewrite a tax year behind the user's back. The folder
     * hints are refreshed along with the path, though: leaving them stale would show
     * an STE-2025 path next to a folder year of 2024 in the inbox.
     */
    private void refreshPathIfMoved(Document document, RemoteDocument item) {
        if (document.getSourcePath().equals(item.path()) && document.getSourceDeletedAt() == null) {
            return;
        }
        FolderConventionRules.FolderHint hint = folderConventionRules.hintFor(item.path());
        documentRepository.save(document.toBuilder()
                .sourcePath(item.path())
                .folderTaxYear(hint.taxYear())
                .folderType(hint.type())
                .sourceDeletedAt(null)
                .build());
    }

    private boolean isInsideRootFolder(DocumentSource source, RemoteDocument item) {
        // Graph's delta feed returns the whole drive and cannot filter by path,
        // so the folder scope is enforced here.
        return item.path().startsWith(source.getRootFolderPath());
    }

    private static boolean isPdf(String filename) {
        return filename.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    /**
     * @param autoApplied documents whose values went straight into a tax year
     * @param needsReview documents waiting for the user
     * @param failed      documents that cannot be processed
     */
    public record SyncResult(int autoApplied, int needsReview, int failed) {

        static SyncResult empty() {
            return new SyncResult(0, 0, 0);
        }

        static SyncResult of(int autoApplied, int needsReview, int failed) {
            return new SyncResult(autoApplied, needsReview, failed);
        }

        SyncResult plus(SyncResult other) {
            return new SyncResult(
                    autoApplied + other.autoApplied,
                    needsReview + other.needsReview,
                    failed + other.failed);
        }
    }
}
