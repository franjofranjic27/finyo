package ch.finyo.transaction;

import ch.finyo.account.Account;
import ch.finyo.account.AccountRepository;
import ch.finyo.account.AccountType;
import ch.finyo.category.Category;
import ch.finyo.category.CategoryRule;
import ch.finyo.category.CategoryRuleMatcher;
import ch.finyo.category.CategoryRuleService;
import ch.finyo.category.CategoryType;
import ch.finyo.common.DocumentProcessingException;
import ch.finyo.common.ResourceNotFoundException;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;

/**
 * Pure unit tests for CsvImportService (no Spring context, no database).
 *
 * The service was recently refactored around processRow()/RowOutcome/ImportContext,
 * so these tests pin down the row-level outcome semantics:
 *   - IMPORTED rows are collected and persisted via a single saveAll()
 *   - SKIPPED  rows (duplicates) never reach the repository
 *   - FAILED   rows produce an error entry (except silently-ignored empty cells)
 *
 * Also covered: preset resolution (UBS/Raiffeisen/PostFinance/custom fallback),
 * Swiss number formats (apostrophe grouping, comma decimal), account ownership,
 * and the Excel ingestion path including header skipping, empty rows and
 * numeric/date/boolean cell conversion.
 */
@ExtendWith(MockitoExtension.class)
class CsvImportServiceTest {

    private static final String USER_ID = "user-import-1";
    private static final UUID ACCOUNT_ID = UUID.randomUUID();

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CategoryRuleService categoryRuleService;

    @InjectMocks
    private CsvImportService csvImportService;

    @Captor
    private ArgumentCaptor<List<Transaction>> savedTransactions;

    @BeforeEach
    void stubEmptyRuleMatcher() {
        // lenient: tests that fail before row processing (e.g. unknown account)
        // never load the matcher
        lenient().when(categoryRuleService.loadMatcher(USER_ID)).thenReturn(CategoryRuleMatcher.empty());
    }

    // -------------------------------------------------------------------------
    // Builders / factories
    // -------------------------------------------------------------------------

    private Account buildAccount() {
        return Account.builder()
                .id(ACCOUNT_ID)
                .userId(USER_ID)
                .name("Import Account")
                .type(AccountType.CHECKING)
                .currency("CHF")
                .initialBalance(BigDecimal.ZERO)
                .build();
    }

    private void givenAccountExists() {
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                .willReturn(Optional.of(buildAccount()));
    }

    /** Custom mapping: date=0, amount=1, description=2, Swiss date format, "." decimal. */
    private ImportRequest customRequest(boolean hasHeader, boolean skipDuplicates) {
        return new ImportRequest(ACCOUNT_ID, null, 0, 1, 2, null, null, null, hasHeader, skipDuplicates);
    }

    private ImportRequest presetRequest(String preset) {
        return new ImportRequest(ACCOUNT_ID, preset, null, null, null, null, null, null, true, false);
    }

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "import.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    // =========================================================================
    // importCsv() — happy path and mapping resolution
    // =========================================================================

    @Test
    void importCsv_imports_valid_rows_and_saves_them_in_one_batch() throws IOException {
        givenAccountExists();
        MockMultipartFile file = csvFile("""
                date,amount,description
                15.03.2025,-42.50,Coffee beans
                16.03.2025,2500.00,Salary March
                """);

        ImportResultResponse result = csvImportService.importCsv(file, customRequest(true, false), USER_ID);

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.skippedDuplicates()).isZero();
        assertThat(result.failed()).isZero();
        assertThat(result.errors()).isEmpty();

        then(transactionRepository).should().saveAll(savedTransactions.capture());
        List<Transaction> saved = savedTransactions.getValue();
        assertThat(saved).hasSize(2);
        Transaction first = saved.get(0);
        assertThat(first.getUserId()).isEqualTo(USER_ID);
        assertThat(first.getAmount()).isEqualByComparingTo("-42.50");
        assertThat(first.getCurrency()).isEqualTo("CHF");
        assertThat(first.getDate()).isEqualTo(LocalDate.of(2025, Month.MARCH, 15));
        assertThat(first.getDescription()).isEqualTo("Coffee beans");
        assertThat(first.getAccount().getId()).isEqualTo(ACCOUNT_ID);
        assertThat(first.getSource()).isEqualTo(TransactionSource.CSV_IMPORT);
    }

    @Test
    void importCsv_without_header_parses_the_first_line_as_data() throws IOException {
        givenAccountExists();
        MockMultipartFile file = csvFile("15.03.2025,-10.00,First line is data\n");

        ImportResultResponse result = csvImportService.importCsv(file, customRequest(false, false), USER_ID);

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.imported()).isEqualTo(1);
    }

    @Test
    void importCsv_detects_semicolon_delimiter() throws IOException {
        givenAccountExists();
        MockMultipartFile file = csvFile("15.03.2025;-99.90;Semicolon separated\n");

        ImportResultResponse result = csvImportService.importCsv(file, customRequest(false, false), USER_ID);

        assertThat(result.imported()).isEqualTo(1);
        then(transactionRepository).should().saveAll(savedTransactions.capture());
        assertThat(savedTransactions.getValue().get(0).getDescription()).isEqualTo("Semicolon separated");
    }

    @Test
    void importCsv_with_ubs_preset_reads_date_col0_amount_col2_description_col4() throws IOException {
        givenAccountExists();
        MockMultipartFile file = csvFile("""
                Datum,Valuta,Betrag,Saldo,Beschreibung
                01.02.2025,01.02.2025,-120.00,880.00,Migros purchase
                """);

        ImportResultResponse result = csvImportService.importCsv(file, presetRequest("UBS"), USER_ID);

        assertThat(result.imported()).isEqualTo(1);
        then(transactionRepository).should().saveAll(savedTransactions.capture());
        Transaction saved = savedTransactions.getValue().get(0);
        assertThat(saved.getAmount()).isEqualByComparingTo("-120.00");
        assertThat(saved.getDate()).isEqualTo(LocalDate.of(2025, Month.FEBRUARY, 1));
        assertThat(saved.getDescription()).isEqualTo("Migros purchase");
    }

    @Test
    void importCsv_with_raiffeisen_preset_reads_amount_from_col1() throws IOException {
        givenAccountExists();
        MockMultipartFile file = csvFile("""
                Datum,Betrag,Text
                05.04.2025,-55.55,Raiffeisen row
                """);

        ImportResultResponse result = csvImportService.importCsv(file, presetRequest("RAIFFEISEN"), USER_ID);

        assertThat(result.imported()).isEqualTo(1);
        then(transactionRepository).should().saveAll(savedTransactions.capture());
        assertThat(savedTransactions.getValue().get(0).getAmount()).isEqualByComparingTo("-55.55");
    }

    @Test
    void importCsv_with_postfinance_preset_reads_description_from_col1_and_amount_from_col2() throws IOException {
        givenAccountExists();
        MockMultipartFile file = csvFile("""
                Datum,Text,Betrag
                05.04.2025,PostFinance row,-77.70
                """);

        ImportResultResponse result = csvImportService.importCsv(file, presetRequest("POSTFINANCE"), USER_ID);

        assertThat(result.imported()).isEqualTo(1);
        then(transactionRepository).should().saveAll(savedTransactions.capture());
        Transaction saved = savedTransactions.getValue().get(0);
        assertThat(saved.getDescription()).isEqualTo("PostFinance row");
        assertThat(saved.getAmount()).isEqualByComparingTo("-77.70");
    }

    @Test
    void importCsv_with_unknown_preset_falls_back_to_custom_column_mapping() throws IOException {
        // "ZKB" is documented in ImportRequest but has no dedicated mapping yet —
        // the switch default must fall back to the request's custom columns.
        givenAccountExists();
        ImportRequest request = new ImportRequest(ACCOUNT_ID, "ZKB", 0, 1, 2, null, null, null, false, false);
        MockMultipartFile file = csvFile("15.03.2025,-10.00,Fallback row\n");

        ImportResultResponse result = csvImportService.importCsv(file, request, USER_ID);

        assertThat(result.imported()).isEqualTo(1);
    }

    @Test
    void importCsv_uses_custom_date_format_and_comma_decimal_separator() throws IOException {
        givenAccountExists();
        ImportRequest request = new ImportRequest(
                ACCOUNT_ID, null, 0, 1, 2, null, "yyyy-MM-dd", ",", false, false);
        MockMultipartFile file = csvFile("2025-03-15;-1'234,56;Swiss formatted\n");

        ImportResultResponse result = csvImportService.importCsv(file, request, USER_ID);

        assertThat(result.imported()).isEqualTo(1);
        then(transactionRepository).should().saveAll(savedTransactions.capture());
        Transaction saved = savedTransactions.getValue().get(0);
        assertThat(saved.getDate()).isEqualTo(LocalDate.of(2025, Month.MARCH, 15));
        assertThat(saved.getAmount()).isEqualByComparingTo("-1234.56");
    }

    @Test
    void importCsv_strips_apostrophe_grouping_separator_from_amounts() throws IOException {
        givenAccountExists();
        MockMultipartFile file = csvFile("15.03.2025,12'500.00,Big amount\n");

        csvImportService.importCsv(file, customRequest(false, false), USER_ID);

        then(transactionRepository).should().saveAll(savedTransactions.capture());
        assertThat(savedTransactions.getValue().get(0).getAmount()).isEqualByComparingTo("12500.00");
    }

    // =========================================================================
    // importCsv() — failure and skip outcomes
    // =========================================================================

    @Test
    void importCsv_throws_ResourceNotFoundException_when_account_belongs_to_another_user() {
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).willReturn(Optional.empty());
        MockMultipartFile file = csvFile("15.03.2025,-10.00,x\n");
        ImportRequest request = customRequest(false, false);

        assertThatThrownBy(() -> csvImportService.importCsv(file, request, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account");
        then(transactionRepository).should(never()).saveAll(any());
    }

    @Test
    void importCsv_counts_row_with_too_few_columns_as_failed_with_error_message() throws IOException {
        givenAccountExists();
        MockMultipartFile file = csvFile("only-one-column\n");

        ImportResultResponse result = csvImportService.importCsv(file, customRequest(false, false), USER_ID);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.imported()).isZero();
        assertThat(result.errors()).containsExactly("Row 1: insufficient columns");
    }

    @Test
    void importCsv_counts_rows_with_blank_date_or_amount_as_failed() throws IOException {
        // univocity converts blank cells to null, so these rows fail with an
        // error entry instead of being silently dropped.
        givenAccountExists();
        MockMultipartFile file = csvFile(" ,-10.00,missing date\n15.03.2025, ,missing amount\n");

        ImportResultResponse result = csvImportService.importCsv(file, customRequest(false, false), USER_ID);

        assertThat(result.failed()).isEqualTo(2);
        assertThat(result.imported()).isZero();
        assertThat(result.errors()).hasSize(2);
    }

    @Test
    void importCsv_counts_unparseable_date_as_failed_and_continues_with_next_row() throws IOException {
        givenAccountExists();
        MockMultipartFile file = csvFile("not-a-date,-10.00,bad row\n15.03.2025,-20.00,good row\n");

        ImportResultResponse result = csvImportService.importCsv(file, customRequest(false, false), USER_ID);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).startsWith("Row 1:");
    }

    @Test
    void importCsv_counts_unparseable_amount_as_failed() throws IOException {
        givenAccountExists();
        MockMultipartFile file = csvFile("15.03.2025,abc,bad amount\n");

        ImportResultResponse result = csvImportService.importCsv(file, customRequest(false, false), USER_ID);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
    }

    @Test
    void importCsv_assigns_the_category_matched_by_the_users_keyword_rules() throws IOException {
        givenAccountExists();
        Category groceries = Category.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .name("Groceries")
                .type(CategoryType.EXPENSE)
                .build();
        given(categoryRuleService.loadMatcher(USER_ID)).willReturn(CategoryRuleMatcher.of(List.of(
                CategoryRule.builder()
                        .id(UUID.randomUUID())
                        .userId(USER_ID)
                        .keyword("migros")
                        .category(groceries)
                        .build())));
        MockMultipartFile file = csvFile("15.03.2025,-42.50,MIGROS Zuerich\n16.03.2025,-10.00,Unknown shop\n");

        ImportResultResponse result = csvImportService.importCsv(file, customRequest(false, false), USER_ID);

        assertThat(result.imported()).isEqualTo(2);
        then(transactionRepository).should().saveAll(savedTransactions.capture());
        List<Transaction> saved = savedTransactions.getValue();
        assertThat(saved.get(0).getCategory()).isEqualTo(groceries);
        assertThat(saved.get(1).getCategory()).isNull();
    }

    @Test
    void importCsv_rejects_files_beyond_the_row_cap() {
        givenAccountExists();
        MockMultipartFile file = csvFile("15.03.2025,-1.00,row\n".repeat(10_001));
        ImportRequest request = customRequest(false, false);

        assertThatThrownBy(() -> csvImportService.importCsv(file, request, USER_ID))
                .isInstanceOf(DocumentProcessingException.class)
                .hasMessageContaining("maximum");
        then(transactionRepository).should(never()).saveAll(any());
    }

    @Test
    void importExcel_of_a_file_that_is_not_a_workbook_is_rejected_without_leaking_parser_internals() {
        givenAccountExists();
        MockMultipartFile file = new MockMultipartFile("file", "statement.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "<?xml version=\"1.0\"?><Document/>".getBytes(StandardCharsets.UTF_8));
        ImportRequest request = customRequest(true, false);

        assertThatThrownBy(() -> csvImportService.importExcel(file, request, USER_ID))
                .isInstanceOf(DocumentProcessingException.class)
                .hasMessage("The file could not be read as an Excel workbook (.xlsx)");
    }

    @Test
    void importCsv_skips_duplicate_rows_when_skipDuplicates_is_enabled() throws IOException {
        givenAccountExists();
        given(transactionRepository.existsByUserIdAndDateAndAmountAndDescription(
                USER_ID, LocalDate.of(2025, Month.MARCH, 15), new BigDecimal("-42.50"), "Coffee"))
                .willReturn(true);
        MockMultipartFile file = csvFile("15.03.2025,-42.50,Coffee\n16.03.2025,-10.00,Tea\n");

        ImportResultResponse result = csvImportService.importCsv(file, customRequest(false, true), USER_ID);

        assertThat(result.skippedDuplicates()).isEqualTo(1);
        assertThat(result.imported()).isEqualTo(1);
        then(transactionRepository).should().saveAll(savedTransactions.capture());
        assertThat(savedTransactions.getValue())
                .hasSize(1)
                .allSatisfy(t -> assertThat(t.getDescription()).isEqualTo("Tea"));
    }

    @Test
    void importCsv_never_checks_for_duplicates_when_skipDuplicates_is_disabled() throws IOException {
        givenAccountExists();
        MockMultipartFile file = csvFile("15.03.2025,-42.50,Coffee\n");

        csvImportService.importCsv(file, customRequest(false, false), USER_ID);

        then(transactionRepository).should(never())
                .existsByUserIdAndDateAndAmountAndDescription(anyString(), any(), any(), anyString());
    }

    @Test
    void importCsv_of_empty_file_returns_all_zero_counts() throws IOException {
        givenAccountExists();
        MockMultipartFile file = csvFile("");

        ImportResultResponse result = csvImportService.importCsv(file, customRequest(false, false), USER_ID);

        assertThat(result.totalRows()).isZero();
        assertThat(result.imported()).isZero();
        assertThat(result.failed()).isZero();
        then(transactionRepository).should().saveAll(List.of());
    }

    @Test
    void importCsv_uses_empty_description_when_description_column_is_missing_in_row() throws IOException {
        // Row has only date and amount; description column index 2 is out of range.
        givenAccountExists();
        MockMultipartFile file = csvFile("15.03.2025,-10.00\n");

        ImportResultResponse result = csvImportService.importCsv(file, customRequest(false, false), USER_ID);

        assertThat(result.imported()).isEqualTo(1);
        then(transactionRepository).should().saveAll(savedTransactions.capture());
        assertThat(savedTransactions.getValue().get(0).getDescription()).isEmpty();
    }

    // =========================================================================
    // importExcel()
    // =========================================================================

    private MockMultipartFile excelFile(ExcelContent content) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            content.fill(workbook, sheet);
            workbook.write(out);
            return new MockMultipartFile("file", "import.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }

    @FunctionalInterface
    private interface ExcelContent {
        void fill(XSSFWorkbook workbook, Sheet sheet);
    }

    @Test
    void importExcel_skips_header_row_and_imports_string_and_numeric_cells() throws IOException {
        givenAccountExists();
        MockMultipartFile file = excelFile((workbook, sheet) -> {
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Datum");
            header.createCell(1).setCellValue("Betrag");
            header.createCell(2).setCellValue("Text");
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue("15.03.2025");
            data.createCell(1).setCellValue(-42.5);
            data.createCell(2).setCellValue("Excel row");
        });

        ImportResultResponse result = csvImportService.importExcel(file, customRequest(true, false), USER_ID);

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.imported()).isEqualTo(1);
        then(transactionRepository).should().saveAll(savedTransactions.capture());
        Transaction saved = savedTransactions.getValue().get(0);
        assertThat(saved.getAmount()).isEqualByComparingTo("-42.5");
        assertThat(saved.getDescription()).isEqualTo("Excel row");
        assertThat(saved.getSource()).isEqualTo(TransactionSource.CSV_IMPORT);
    }

    @Test
    void importExcel_converts_date_formatted_cells_to_iso_dates() throws IOException {
        givenAccountExists();
        // Date-formatted numeric cells are converted to ISO yyyy-MM-dd strings,
        // so the mapping must use the ISO date format.
        ImportRequest request = new ImportRequest(
                ACCOUNT_ID, null, 0, 1, 2, null, "yyyy-MM-dd", ".", true, false);
        MockMultipartFile file = excelFile((workbook, sheet) -> {
            CreationHelper helper = workbook.getCreationHelper();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("dd.mm.yyyy"));

            sheet.createRow(0).createCell(0).setCellValue("header");
            Row data = sheet.createRow(1);
            var dateCell = data.createCell(0);
            dateCell.setCellValue(LocalDateTime.of(2025, Month.JUNE, 30, 0, 0));
            dateCell.setCellStyle(dateStyle);
            data.createCell(1).setCellValue(-15.0);
            data.createCell(2).setCellValue("Dated row");
        });

        ImportResultResponse result = csvImportService.importExcel(file, request, USER_ID);

        assertThat(result.imported()).isEqualTo(1);
        then(transactionRepository).should().saveAll(savedTransactions.capture());
        assertThat(savedTransactions.getValue().get(0).getDate())
                .isEqualTo(LocalDate.of(2025, Month.JUNE, 30));
    }

    @Test
    void importExcel_ignores_physically_missing_rows_and_treats_boolean_cells_as_text() throws IOException {
        givenAccountExists();
        MockMultipartFile file = excelFile((workbook, sheet) -> {
            sheet.createRow(0).createCell(0).setCellValue("header");
            Row first = sheet.createRow(1);
            first.createCell(0).setCellValue("15.03.2025");
            first.createCell(1).setCellValue(-1.0);
            first.createCell(2).setCellValue(true);
            // Row index 2 is intentionally never created → sheet.getRow(2) == null
            Row third = sheet.createRow(3);
            third.createCell(0).setCellValue("16.03.2025");
            third.createCell(1).setCellValue(-2.0);
            third.createCell(2).setCellValue("after gap");
        });

        ImportResultResponse result = csvImportService.importExcel(file, customRequest(true, false), USER_ID);

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.imported()).isEqualTo(2);
        then(transactionRepository).should().saveAll(savedTransactions.capture());
        assertThat(savedTransactions.getValue().get(0).getDescription()).isEqualTo("true");
    }

    @Test
    void importExcel_counts_rows_with_empty_date_or_amount_cells_as_failed_without_error_entry() throws IOException {
        // Missing Excel cells become empty strings (not null as in CSV), which
        // the row processor silently counts as failed — no error entry.
        givenAccountExists();
        MockMultipartFile file = excelFile((workbook, sheet) -> {
            sheet.createRow(0).createCell(0).setCellValue("header");
            Row missingAmount = sheet.createRow(1);
            missingAmount.createCell(0).setCellValue("15.03.2025");
            missingAmount.createCell(1).setCellValue("");
            missingAmount.createCell(2).setCellValue("no amount");
        });

        ImportResultResponse result = csvImportService.importExcel(file, customRequest(true, false), USER_ID);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.imported()).isZero();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void importExcel_throws_ResourceNotFoundException_when_account_is_not_found() throws IOException {
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).willReturn(Optional.empty());
        MockMultipartFile file = excelFile((workbook, sheet) ->
                sheet.createRow(0).createCell(0).setCellValue("header"));
        ImportRequest request = customRequest(true, false);

        assertThatThrownBy(() -> csvImportService.importExcel(file, request, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account");
    }
}
