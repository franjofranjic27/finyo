package ch.finyo.transaction;

import ch.finyo.account.Account;
import ch.finyo.account.AccountRepository;
import ch.finyo.category.Category;
import ch.finyo.category.CategoryRuleMatcher;
import ch.finyo.category.CategoryRuleService;
import ch.finyo.common.ResourceNotFoundException;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsvImportService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRuleService categoryRuleService;

    @Transactional
    public ImportResultResponse importCsv(MultipartFile file, ImportRequest request, String userId) throws IOException {
        Account account = accountRepository.findByIdAndUserId(request.accountId(), userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Account", request.accountId()));

        CsvColumnMapping mapping = resolveMapping(request);
        List<String[]> rows = readCsvRows(file, mapping);

        return processRows(rows, mapping, account, userId, request.skipDuplicates());
    }

    @Transactional
    public ImportResultResponse importExcel(MultipartFile file, ImportRequest request, String userId) throws IOException {
        Account account = accountRepository.findByIdAndUserId(request.accountId(), userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Account", request.accountId()));

        CsvColumnMapping mapping = resolveMapping(request);
        List<String[]> rows = readExcelRows(file, mapping);

        return processRows(rows, mapping, account, userId, request.skipDuplicates());
    }

    /** Reads the raw CSV cell matrix (header stripped when configured). Shared with the preview flow. */
    List<String[]> readCsvRows(MultipartFile file, CsvColumnMapping mapping) throws IOException {
        CsvParserSettings settings = new CsvParserSettings();
        settings.setHeaderExtractionEnabled(mapping.hasHeader());
        settings.setDelimiterDetectionEnabled(true, ',', ';');

        CsvParser parser = new CsvParser(settings);
        return parser.parseAll(file.getInputStream(), "UTF-8");
    }

    /** Reads the raw cell matrix of the first sheet. Shared with the preview flow. */
    List<String[]> readExcelRows(MultipartFile file, CsvColumnMapping mapping) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            int startRow = mapping.hasHeader() ? 1 : 0;
            for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String[] rowData = new String[row.getLastCellNum()];
                for (int j = 0; j < row.getLastCellNum(); j++) {
                    Cell cell = row.getCell(j);
                    rowData[j] = cell != null ? getCellValueAsString(cell) : "";
                }
                rows.add(rowData);
            }
        }
        return rows;
    }

    private enum RowOutcome {
        IMPORTED, SKIPPED, FAILED
    }

    /** Date, amount and description of a single successfully parsed statement row — nothing persisted. */
    record ParsedRow(LocalDate date, BigDecimal amount, String description) {
    }

    /** Immutable per-import settings shared by every row. */
    private record ImportContext(CsvColumnMapping mapping, DateTimeFormatter formatter, Account account,
                                 String userId, boolean skipDuplicates, CategoryRuleMatcher matcher) {
    }

    private ImportResultResponse processRows(List<String[]> rows, CsvColumnMapping mapping, Account account, String userId, boolean skipDuplicates) {
        int imported = 0;
        int skipped = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        List<Transaction> toSave = new ArrayList<>();

        ImportContext context = new ImportContext(
                mapping, DateTimeFormatter.ofPattern(mapping.dateFormat()), account, userId, skipDuplicates,
                categoryRuleService.loadMatcher(userId));

        for (int i = 0; i < rows.size(); i++) {
            switch (processRow(rows.get(i), i + 1, context, toSave, errors)) {
                case IMPORTED -> imported++;
                case SKIPPED -> skipped++;
                case FAILED -> failed++;
            }
        }

        transactionRepository.saveAll(toSave);
        log.info("CSV import: {} imported, {} skipped, {} failed", imported, skipped, failed);
        return new ImportResultResponse(rows.size(), imported, skipped, failed, errors);
    }

    private RowOutcome processRow(String[] row, int rowNumber, ImportContext context,
                                  List<Transaction> toSave, List<String> errors) {
        try {
            Optional<ParsedRow> maybeParsed = parseRow(row, context.mapping(), context.formatter());
            if (maybeParsed.isEmpty()) {
                return RowOutcome.FAILED;
            }
            ParsedRow parsed = maybeParsed.get();

            if (context.skipDuplicates() && transactionRepository.existsByUserIdAndDateAndAmountAndDescription(
                    context.userId(), parsed.date(), parsed.amount(), parsed.description())) {
                return RowOutcome.SKIPPED;
            }

            Category category = context.matcher().match(parsed.description()).orElse(null);
            toSave.add(Transaction.builder()
                    .userId(context.userId())
                    .amount(parsed.amount())
                    .currency("CHF")
                    .date(parsed.date())
                    .description(parsed.description())
                    .category(category)
                    .account(context.account())
                    .source(TransactionSource.CSV_IMPORT)
                    .build());
            return RowOutcome.IMPORTED;
        } catch (Exception e) {
            errors.add("Row " + rowNumber + ": " + e.getMessage());
            return RowOutcome.FAILED;
        }
    }

    /**
     * Parses one raw statement row without saving anything. Returns empty when
     * the date or amount cell is blank (silently skipped, matching the legacy
     * behavior) and throws for structurally broken rows (missing columns,
     * unparseable date or amount). Shared with the preview flow.
     */
    Optional<ParsedRow> parseRow(String[] row, CsvColumnMapping mapping, DateTimeFormatter formatter) {
        if (row.length <= Math.max(mapping.dateColumn(), mapping.amountColumn())) {
            throw new IllegalArgumentException("insufficient columns");
        }

        String dateStr = row[mapping.dateColumn()].trim();
        String amountStr = row[mapping.amountColumn()].trim();
        String description = mapping.descriptionColumn() >= 0 && row.length > mapping.descriptionColumn()
                && row[mapping.descriptionColumn()] != null
                ? row[mapping.descriptionColumn()].trim() : "";

        if (dateStr.isEmpty() || amountStr.isEmpty()) {
            return Optional.empty();
        }

        LocalDate date = LocalDate.parse(dateStr, formatter);
        BigDecimal amount = parseAmount(amountStr, mapping.decimalSeparator(), mapping.groupingSeparator());
        return Optional.of(new ParsedRow(date, amount, description));
    }

    private BigDecimal parseAmount(String raw, String decimalSep, String groupSep) {
        String cleaned = raw.replace(groupSep != null ? groupSep : "'", "")
                            .replace(" ", "");
        if (",".equals(decimalSep)) {
            cleaned = cleaned.replace(",", ".");
        }
        return new BigDecimal(cleaned);
    }

    private String getCellValueAsString(Cell cell) {
        return switch (cell.getCellType()) {
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                yield String.valueOf(cell.getNumericCellValue());
            }
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    CsvColumnMapping resolveMapping(ImportRequest request) {
        if (request.preset() != null) {
            return switch (request.preset().toUpperCase()) {
                case "UBS" -> CsvColumnMapping.ubs();
                case "RAIFFEISEN" -> CsvColumnMapping.raiffeisen();
                case "POSTFINANCE" -> CsvColumnMapping.postfinance();
                default -> buildCustomMapping(request);
            };
        }
        return buildCustomMapping(request);
    }

    private CsvColumnMapping buildCustomMapping(ImportRequest request) {
        return new CsvColumnMapping(
                request.dateColumn() != null ? request.dateColumn() : 0,
                request.amountColumn() != null ? request.amountColumn() : 1,
                request.descriptionColumn() != null ? request.descriptionColumn() : 2,
                request.currencyColumn() != null ? request.currencyColumn() : -1,
                request.dateFormat() != null ? request.dateFormat() : CsvColumnMapping.SWISS_DATE_FORMAT,
                request.decimalSeparator() != null ? request.decimalSeparator() : ".",
                "'",
                request.hasHeader(),
                "CUSTOM"
        );
    }
}
