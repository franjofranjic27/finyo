package ch.finyo.transaction;

import ch.finyo.BaseIntegrationTest;
import ch.finyo.account.Account;
import ch.finyo.account.AccountRepository;
import ch.finyo.account.AccountType;
import ch.finyo.category.CategoryRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the multipart import endpoints
 * POST /api/v1/transactions/import/csv and /import/excel.
 *
 * Complements TransactionEdgeCaseIT (which covers the JSON CRUD endpoints)
 * with the file-upload path: request-parameter mapping, duplicate skipping
 * on a real database unique lookup, and account ownership.
 */
class TransactionImportIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Account account;

    @BeforeEach
    void seedAccount() {
        transactionRepository.deleteAll();
        categoryRepository.deleteAll();
        accountRepository.deleteAll();
        account = accountRepository.save(Account.builder()
                .userId(TEST_USER_ID)
                .name("Import Account")
                .type(AccountType.CHECKING)
                .currency("CHF")
                .initialBalance(BigDecimal.ZERO)
                .build());
    }

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "import.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void csv_import_persists_valid_rows_for_the_users_account() throws Exception {
        MockMultipartFile file = csvFile("""
                Datum,Betrag,Text
                15.03.2025,-42.50,Coffee beans
                16.03.2025,2500.00,Salary March
                """);

        mockMvc.perform(multipart("/api/v1/transactions/import/csv")
                        .file(file)
                        .param("accountId", account.getId().toString())
                        .with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows", is(2)))
                .andExpect(jsonPath("$.imported", is(2)))
                .andExpect(jsonPath("$.failed", is(0)));

        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Test
    void csv_import_skips_rows_that_already_exist_when_skipDuplicates_is_on() throws Exception {
        MockMultipartFile file = csvFile("""
                Datum,Betrag,Text
                15.03.2025,-42.50,Coffee beans
                """);

        mockMvc.perform(multipart("/api/v1/transactions/import/csv")
                        .file(file)
                        .param("accountId", account.getId().toString())
                        .with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported", is(1)));

        // Second upload of the same file: the row is now a duplicate
        mockMvc.perform(multipart("/api/v1/transactions/import/csv")
                        .file(file)
                        .param("accountId", account.getId().toString())
                        .param("skipDuplicates", "true")
                        .with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported", is(0)))
                .andExpect(jsonPath("$.skippedDuplicates", is(1)));

        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    void csv_import_into_a_foreign_account_returns_404_and_persists_nothing() throws Exception {
        Account foreignAccount = accountRepository.save(Account.builder()
                .userId(OTHER_USER_ID)
                .name("Foreign Account")
                .type(AccountType.CHECKING)
                .currency("CHF")
                .initialBalance(BigDecimal.ZERO)
                .build());
        MockMultipartFile file = csvFile("Datum,Betrag,Text\n15.03.2025,-42.50,Coffee\n");

        mockMvc.perform(multipart("/api/v1/transactions/import/csv")
                        .file(file)
                        .param("accountId", foreignAccount.getId().toString())
                        .with(asUser()))
                .andExpect(status().isNotFound());

        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void csv_import_without_an_account_id_returns_400() throws Exception {
        mockMvc.perform(multipart("/api/v1/transactions/import/csv")
                        .file(csvFile("Datum,Betrag,Text\n"))
                        .with(asUser()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void excel_import_persists_rows_from_the_first_sheet() throws Exception {
        byte[] workbookBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Datum");
            header.createCell(1).setCellValue("Betrag");
            header.createCell(2).setCellValue("Text");
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue("15.03.2025");
            data.createCell(1).setCellValue(-42.5);
            data.createCell(2).setCellValue("Excel expense");
            workbook.write(out);
            workbookBytes = out.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbookBytes);

        mockMvc.perform(multipart("/api/v1/transactions/import/excel")
                        .file(file)
                        .param("accountId", account.getId().toString())
                        .with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported", is(1)));

        Transaction imported = transactionRepository.findAll().get(0);
        assertThat(imported.getAmount()).isEqualByComparingTo("-42.5");
        assertThat(imported.getSource()).isEqualTo(TransactionSource.CSV_IMPORT);
        assertThat(imported.getUserId()).isEqualTo(TEST_USER_ID);
    }

    @Test
    void excel_import_into_an_unknown_account_returns_404() throws Exception {
        byte[] workbookBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.createSheet("Sheet1");
            workbook.write(out);
            workbookBytes = out.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile("file", "import.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbookBytes);

        mockMvc.perform(multipart("/api/v1/transactions/import/excel")
                        .file(file)
                        .param("accountId", UUID.randomUUID().toString())
                        .with(asUser()))
                .andExpect(status().isNotFound());
    }
}
