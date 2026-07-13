package ch.finyo.transaction;

import ch.finyo.account.Account;
import ch.finyo.account.AccountRepository;
import ch.finyo.category.Category;
import ch.finyo.category.CategoryRepository;
import ch.finyo.category.CategoryRuleMatcher;
import ch.finyo.category.CategoryRuleService;
import ch.finyo.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Two-step statement import: {@link #preview} parses a file (CSV, Excel or
 * camt.053), flags duplicates and suggests categories via the user's keyword
 * rules — persisting nothing. {@link #commit} then saves the rows the user
 * confirmed, re-checking duplicates against the database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionImportService {

    private static final int DETECTION_HEAD_BYTES = 32;
    private static final String DEFAULT_CURRENCY = "CHF";

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryRuleService categoryRuleService;
    private final CsvImportService csvImportService;
    private final CamtParser camtParser;

    /** A statement row as parsed from the file, before duplicate detection and category matching. */
    private record ParsedRow(LocalDate date, BigDecimal amount, @Nullable String currency,
                             @Nullable String description, @Nullable String counterparty,
                             @Nullable String externalRef) implements DuplicateCandidate {
    }

    @Transactional(readOnly = true)
    public ImportPreviewResponse preview(MultipartFile file, UUID accountId, ImportRequest csvParams,
                                         @Nullable ImportFormat formatOverride, String userId) throws IOException {
        requireOwnedAccount(accountId, userId);

        ImportFormat format = formatOverride != null
                ? formatOverride
                : ImportFormat.detect(file.getOriginalFilename(), readHead(file));
        CategoryRuleMatcher matcher = categoryRuleService.loadMatcher(userId);

        return switch (format) {
            case CAMT053 -> previewCamt(file, matcher, userId);
            case CSV, EXCEL -> previewTabular(file, format, csvParams, matcher, userId);
        };
    }

    private ImportPreviewResponse previewCamt(MultipartFile file, CategoryRuleMatcher matcher, String userId)
            throws IOException {
        List<ParsedRow> rows = camtParser.parse(file.getBytes()).stream()
                .map(entry -> new ParsedRow(entry.date(), entry.amount(), entry.currency(),
                        truncateDescription(entry.description()), entry.counterparty(), entry.externalRef()))
                .toList();
        return buildPreviewResponse(ImportFormat.CAMT053, rows, 0, List.of(), matcher, userId);
    }

    private ImportPreviewResponse previewTabular(MultipartFile file, ImportFormat format, ImportRequest csvParams,
                                                 CategoryRuleMatcher matcher, String userId) throws IOException {
        CsvColumnMapping mapping = csvImportService.resolveMapping(csvParams);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(mapping.dateFormat());
        List<String[]> rawRows = format == ImportFormat.EXCEL
                ? csvImportService.readExcelRows(file, mapping)
                : csvImportService.readCsvRows(file, mapping);

        List<ParsedRow> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int failed = 0;
        for (int i = 0; i < rawRows.size(); i++) {
            try {
                var maybeParsed = csvImportService.parseRow(rawRows.get(i), mapping, formatter);
                if (maybeParsed.isEmpty()) {
                    failed++;
                    continue;
                }
                var parsed = maybeParsed.get();
                rows.add(new ParsedRow(parsed.date(), parsed.amount(), null,
                        truncateDescription(parsed.description()), null, null));
            } catch (Exception e) {
                failed++;
                errors.add("Row " + (i + 1) + ": " + e.getMessage());
            }
        }
        return buildPreviewResponse(format, rows, failed, errors, matcher, userId);
    }

    private ImportPreviewResponse buildPreviewResponse(ImportFormat format, List<ParsedRow> parsedRows, int failed,
                                                       List<String> errors, CategoryRuleMatcher matcher,
                                                       String userId) {
        DuplicateIndex duplicates = loadDuplicateIndex(userId, parsedRows);

        List<ImportPreviewRow> rows = new ArrayList<>(parsedRows.size());
        for (ParsedRow row : parsedRows) {
            Category suggested = matcher.match(matchText(row.description(), row.counterparty())).orElse(null);
            rows.add(new ImportPreviewRow(
                    rows.size() + 1, row.date(), row.amount(), row.currency(), row.description(),
                    row.counterparty(), row.externalRef(), duplicates.contains(row),
                    suggested != null ? suggested.getId() : null,
                    suggested != null ? suggested.getName() : null));
        }

        int duplicateCount = (int) rows.stream().filter(ImportPreviewRow::duplicate).count();
        log.info("Statement import preview: format={} rows={} duplicates={} failed={}",
                format, rows.size(), duplicateCount, failed);
        return new ImportPreviewResponse(
                format, rows.size() + failed, rows.size() - duplicateCount, duplicateCount, failed, rows, errors);
    }

    @Transactional
    public ImportResultResponse commit(ImportCommitRequest request, String userId) {
        Account account = requireOwnedAccount(request.accountId(), userId);
        Map<UUID, Category> categoriesById = resolveOwnedCategories(request.rows(), userId);
        TransactionSource source = request.format() == ImportFormat.CAMT053
                ? TransactionSource.CAMT_IMPORT
                : TransactionSource.CSV_IMPORT;
        DuplicateIndex duplicates = loadDuplicateIndex(userId, request.rows());

        int skipped = 0;
        Set<String> referencesInBatch = new HashSet<>();
        List<Transaction> toSave = new ArrayList<>();

        for (ImportCommitRow row : request.rows()) {
            if (isAlreadyReferenced(row, duplicates, referencesInBatch)
                    || (request.skipDuplicates() && duplicates.contains(row))) {
                skipped++;
                continue;
            }
            toSave.add(Transaction.builder()
                    .userId(userId)
                    .amount(row.amount())
                    .currency(StringUtils.hasText(row.currency()) ? row.currency() : DEFAULT_CURRENCY)
                    .date(row.date())
                    .description(row.description())
                    .category(row.categoryId() != null ? categoriesById.get(row.categoryId()) : null)
                    .account(account)
                    .source(source)
                    .externalRef(row.externalRef())
                    .build());
        }

        transactionRepository.saveAll(toSave);
        log.info("Statement import commit: format={} {} imported, {} skipped for user={}",
                request.format(), toSave.size(), skipped, userId);
        return new ImportResultResponse(request.rows().size(), toSave.size(), skipped, 0, List.of());
    }

    /**
     * Rows whose bank reference is already stored — or repeats earlier in the same batch — can never be
     * inserted: the partial unique index on (user_id, external_ref) would reject them and roll the whole
     * commit back. They are therefore skipped even when the user opted out of duplicate skipping.
     */
    private boolean isAlreadyReferenced(ImportCommitRow row, DuplicateIndex duplicates,
                                        Set<String> referencesInBatch) {
        String externalRef = row.externalRef();
        if (!StringUtils.hasText(externalRef)) {
            return false;
        }
        return duplicates.containsReference(externalRef) || !referencesInBatch.add(externalRef);
    }

    // -------------------------------------------------------------------------
    // Duplicate detection
    // -------------------------------------------------------------------------

    /**
     * Duplicate lookup for a whole import: rows with a bank reference are matched against the
     * references already stored, rows without one against the (date, amount, description)
     * fingerprints of the batch's date range. Two queries per import instead of one per row.
     */
    private record DuplicateIndex(Set<String> existingReferences, Set<TransactionFingerprint> existingFingerprints) {

        boolean contains(DuplicateCandidate row) {
            String externalRef = row.externalRef();
            return StringUtils.hasText(externalRef)
                    ? containsReference(externalRef)
                    : existingFingerprints.contains(TransactionFingerprint.of(row));
        }

        boolean containsReference(String externalRef) {
            return existingReferences.contains(externalRef);
        }
    }

    private DuplicateIndex loadDuplicateIndex(String userId, List<? extends DuplicateCandidate> rows) {
        Set<String> references = rows.stream()
                .map(DuplicateCandidate::externalRef)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        Set<String> existingReferences = references.isEmpty()
                ? Set.of()
                : Set.copyOf(transactionRepository.findExistingExternalRefs(userId, references));

        List<LocalDate> datesWithoutReference = rows.stream()
                .filter(row -> !StringUtils.hasText(row.externalRef()))
                .map(DuplicateCandidate::date)
                .toList();
        if (datesWithoutReference.isEmpty()) {
            return new DuplicateIndex(existingReferences, Set.of());
        }
        LocalDate from = datesWithoutReference.stream().min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = datesWithoutReference.stream().max(Comparator.naturalOrder()).orElseThrow();
        return new DuplicateIndex(existingReferences,
                Set.copyOf(transactionRepository.findFingerprintsByUserIdAndDateBetween(userId, from, to)));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Account requireOwnedAccount(UUID accountId, String userId) {
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Account", accountId));
    }

    private Map<UUID, Category> resolveOwnedCategories(List<ImportCommitRow> rows, String userId) {
        Map<UUID, Category> categoriesById = new HashMap<>();
        for (ImportCommitRow row : rows) {
            UUID categoryId = row.categoryId();
            if (categoryId == null || categoriesById.containsKey(categoryId)) {
                continue;
            }
            Category category = categoryRepository.findByIdAndUserId(categoryId, userId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Category", categoryId));
            categoriesById.put(categoryId, category);
        }
        return categoriesById;
    }

    private static @Nullable String truncateDescription(@Nullable String description) {
        if (description == null || description.length() <= ImportLimits.MAX_DESCRIPTION_LENGTH) {
            return description;
        }
        return description.substring(0, ImportLimits.MAX_DESCRIPTION_LENGTH);
    }

    /** Rules match against description and counterparty combined. */
    private static String matchText(@Nullable String description, @Nullable String counterparty) {
        if (description == null) {
            return counterparty != null ? counterparty : "";
        }
        return counterparty == null ? description : description + " " + counterparty;
    }

    private static byte @Nullable [] readHead(MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(DETECTION_HEAD_BYTES);
        }
    }
}
