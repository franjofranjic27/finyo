package ch.finyo.transaction;

import ch.finyo.account.Account;
import ch.finyo.account.AccountRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class CsvImportService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public ImportResultResponse importCsv(MultipartFile file, ImportRequest request, String userId) throws IOException {
        Account account = accountRepository.findByIdAndUserId(request.accountId(), userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Account", request.accountId()));

        CsvColumnMapping mapping = resolveMapping(request);

        CsvParserSettings settings = new CsvParserSettings();
        settings.setHeaderExtractionEnabled(mapping.hasHeader());
        settings.setDelimiterDetectionEnabled(true, ',', ';');

        CsvParser parser = new CsvParser(settings);
        List<String[]> rows = parser.parseAll(file.getInputStream(), "UTF-8");

        return processRows(rows, mapping, account, userId, request.skipDuplicates());
    }

    @Transactional
    public ImportResultResponse importExcel(MultipartFile file, ImportRequest request, String userId) throws IOException {
        Account account = accountRepository.findByIdAndUserId(request.accountId(), userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Account", request.accountId()));

        CsvColumnMapping mapping = resolveMapping(request);
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

        return processRows(rows, mapping, account, userId, request.skipDuplicates());
    }

    private enum RowOutcome {
        IMPORTED, SKIPPED, FAILED
    }

    /** Immutable per-import settings shared by every row. */
    private record ImportContext(CsvColumnMapping mapping, DateTimeFormatter formatter,
                                 Account account, String userId, boolean skipDuplicates) {
    }

    private ImportResultResponse processRows(List<String[]> rows, CsvColumnMapping mapping, Account account, String userId, boolean skipDuplicates) {
        int imported = 0;
        int skipped = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        List<Transaction> toSave = new ArrayList<>();

        ImportContext context = new ImportContext(
                mapping, DateTimeFormatter.ofPattern(mapping.dateFormat()), account, userId, skipDuplicates);

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
        CsvColumnMapping mapping = context.mapping();
        try {
            if (row.length <= Math.max(mapping.dateColumn(), mapping.amountColumn())) {
                errors.add("Row " + rowNumber + ": insufficient columns");
                return RowOutcome.FAILED;
            }

            String dateStr = row[mapping.dateColumn()].trim();
            String amountStr = row[mapping.amountColumn()].trim();
            String description = mapping.descriptionColumn() >= 0 && row.length > mapping.descriptionColumn()
                    ? row[mapping.descriptionColumn()].trim() : "";

            if (dateStr.isEmpty() || amountStr.isEmpty()) {
                return RowOutcome.FAILED;
            }

            LocalDate date = LocalDate.parse(dateStr, context.formatter());
            BigDecimal amount = parseAmount(amountStr, mapping.decimalSeparator(), mapping.groupingSeparator());

            if (context.skipDuplicates() && transactionRepository.existsByUserIdAndDateAndAmountAndDescription(
                    context.userId(), date, amount, description)) {
                return RowOutcome.SKIPPED;
            }

            toSave.add(Transaction.builder()
                    .userId(context.userId())
                    .amount(amount)
                    .currency("CHF")
                    .date(date)
                    .description(description)
                    .account(context.account())
                    .source(TransactionSource.CSV_IMPORT)
                    .build());
            return RowOutcome.IMPORTED;
        } catch (Exception e) {
            errors.add("Row " + rowNumber + ": " + e.getMessage());
            return RowOutcome.FAILED;
        }
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

    private CsvColumnMapping resolveMapping(ImportRequest request) {
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
