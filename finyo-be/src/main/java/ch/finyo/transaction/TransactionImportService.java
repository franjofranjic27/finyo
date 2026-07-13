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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

    /** Preview descriptions are truncated to this length so a commit round-trip always validates. */
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int DETECTION_HEAD_BYTES = 32;
    private static final String DEFAULT_CURRENCY = "CHF";

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryRuleService categoryRuleService;
    private final CsvImportService csvImportService;
    private final CamtParser camtParser;

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
        List<ImportPreviewRow> rows = new ArrayList<>();
        for (CamtEntry entry : camtParser.parse(file.getBytes())) {
            rows.add(toPreviewRow(rows.size() + 1, entry.date(), entry.amount(), entry.currency(),
                    entry.description(), entry.counterparty(), entry.externalRef(), matcher, userId));
        }
        return buildPreviewResponse(ImportFormat.CAMT053, rows, 0, List.of());
    }

    private ImportPreviewResponse previewTabular(MultipartFile file, ImportFormat format, ImportRequest csvParams,
                                                 CategoryRuleMatcher matcher, String userId) throws IOException {
        CsvColumnMapping mapping = csvImportService.resolveMapping(csvParams);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(mapping.dateFormat());
        List<String[]> rawRows = format == ImportFormat.EXCEL
                ? csvImportService.readExcelRows(file, mapping)
                : csvImportService.readCsvRows(file, mapping);

        List<ImportPreviewRow> rows = new ArrayList<>();
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
                rows.add(toPreviewRow(rows.size() + 1, parsed.date(), parsed.amount(), null,
                        parsed.description(), null, null, matcher, userId));
            } catch (Exception e) {
                failed++;
                errors.add("Row " + (i + 1) + ": " + e.getMessage());
            }
        }
        return buildPreviewResponse(format, rows, failed, errors);
    }

    private ImportPreviewRow toPreviewRow(int index, LocalDate date, BigDecimal amount, @Nullable String currency,
                                          @Nullable String description, @Nullable String counterparty,
                                          @Nullable String externalRef, CategoryRuleMatcher matcher, String userId) {
        String truncatedDescription = truncate(description);
        boolean duplicate = isDuplicate(userId, date, amount, truncatedDescription, externalRef);
        Category suggested = matcher.match(matchText(truncatedDescription, counterparty)).orElse(null);
        return new ImportPreviewRow(
                index, date, amount, currency, truncatedDescription, counterparty, externalRef, duplicate,
                suggested != null ? suggested.getId() : null,
                suggested != null ? suggested.getName() : null);
    }

    private ImportPreviewResponse buildPreviewResponse(ImportFormat format, List<ImportPreviewRow> rows,
                                                       int failed, List<String> errors) {
        int duplicates = (int) rows.stream().filter(ImportPreviewRow::duplicate).count();
        log.info("Statement import preview: format={} rows={} duplicates={} failed={}",
                format, rows.size(), duplicates, failed);
        return new ImportPreviewResponse(
                format, rows.size() + failed, rows.size() - duplicates, duplicates, failed, rows, errors);
    }

    @Transactional
    public ImportResultResponse commit(ImportCommitRequest request, String userId) {
        Account account = requireOwnedAccount(request.accountId(), userId);
        Map<UUID, Category> categoriesById = resolveOwnedCategories(request.rows(), userId);
        TransactionSource source = request.format() == ImportFormat.CAMT053
                ? TransactionSource.CAMT_IMPORT
                : TransactionSource.CSV_IMPORT;

        int skipped = 0;
        // guards against repeated references within one batch (e.g. a
        // Sammelbuchung whose sub-transactions all fall back to the entry
        // reference) — the partial unique index would reject the insert
        Set<String> referencesInBatch = new HashSet<>();
        List<Transaction> toSave = new ArrayList<>();

        for (ImportCommitRow row : request.rows()) {
            boolean duplicate = isDuplicate(userId, row.date(), row.amount(), row.description(), row.externalRef())
                    || (row.externalRef() != null && referencesInBatch.contains(row.externalRef()));
            if (duplicate && request.skipDuplicates()) {
                skipped++;
                continue;
            }
            if (row.externalRef() != null) {
                referencesInBatch.add(row.externalRef());
            }
            toSave.add(Transaction.builder()
                    .userId(userId)
                    .amount(row.amount())
                    .currency(row.currency() != null && !row.currency().isBlank() ? row.currency() : DEFAULT_CURRENCY)
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

    private boolean isDuplicate(String userId, LocalDate date, BigDecimal amount,
                                @Nullable String description, @Nullable String externalRef) {
        if (externalRef != null && !externalRef.isBlank()) {
            return transactionRepository.existsByUserIdAndExternalRef(userId, externalRef);
        }
        return transactionRepository.existsByUserIdAndDateAndAmountAndDescription(userId, date, amount, description);
    }

    private static @Nullable String truncate(@Nullable String description) {
        if (description == null || description.length() <= MAX_DESCRIPTION_LENGTH) {
            return description;
        }
        return description.substring(0, MAX_DESCRIPTION_LENGTH);
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
