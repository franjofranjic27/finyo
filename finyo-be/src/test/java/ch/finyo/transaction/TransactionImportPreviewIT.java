package ch.finyo.transaction;

import ch.finyo.BaseIntegrationTest;
import ch.finyo.account.Account;
import ch.finyo.account.AccountRepository;
import ch.finyo.account.AccountType;
import ch.finyo.category.Category;
import ch.finyo.category.CategoryRepository;
import ch.finyo.category.CategoryRule;
import ch.finyo.category.CategoryRuleRepository;
import ch.finyo.category.CategoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Month;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for POST /api/v1/transactions/import/preview: format
 * detection over multipart uploads (CSV and camt.053), duplicate flagging
 * against real database rows, rule-based category suggestions, tenant
 * isolation — and above all that a preview never persists anything.
 */
class TransactionImportPreviewIT extends BaseIntegrationTest {

    /** Fully synthetic camt.053.001.04 statement with one debit entry. */
    private static final String CAMT_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.04">
              <BkToCstmrStmt><Stmt>
                <Ntry>
                  <Amt Ccy="CHF">42.50</Amt>
                  <CdtDbtInd>DBIT</CdtDbtInd>
                  <Sts>BOOK</Sts>
                  <BookgDt><Dt>2025-03-15</Dt></BookgDt>
                  <AcctSvcrRef>SYNTH-IT-REF-1</AcctSvcrRef>
                  <NtryDtls><TxDtls>
                    <RltdPties><Cdtr><Nm>Migros Testfiliale</Nm></Cdtr></RltdPties>
                    <RmtInf><Ustrd>Einkauf Lebensmittel</Ustrd></RmtInf>
                  </TxDtls></NtryDtls>
                </Ntry>
              </Stmt></BkToCstmrStmt>
            </Document>
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CategoryRuleRepository categoryRuleRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Account account;

    @BeforeEach
    void seedAccount() {
        transactionRepository.deleteAll();
        categoryRuleRepository.deleteAll();
        categoryRepository.deleteAll();
        accountRepository.deleteAll();
        account = accountRepository.save(Account.builder()
                .userId(TEST_USER_ID)
                .name("Preview Account")
                .type(AccountType.CHECKING)
                .currency("CHF")
                .initialBalance(BigDecimal.ZERO)
                .build());
    }

    private Category seedGroceriesWithRule(String keyword) {
        Category groceries = categoryRepository.save(Category.builder()
                .userId(TEST_USER_ID)
                .name("Groceries")
                .type(CategoryType.EXPENSE)
                .build());
        categoryRuleRepository.save(CategoryRule.builder()
                .userId(TEST_USER_ID)
                .keyword(keyword)
                .category(groceries)
                .build());
        return groceries;
    }

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "import.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile camtFile(String content) {
        return new MockMultipartFile("file", "statement.xml", "application/xml",
                content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void csv_preview_returns_rows_with_suggestions_and_persists_nothing() throws Exception {
        Category groceries = seedGroceriesWithRule("coffee");

        mockMvc.perform(multipart("/api/v1/transactions/import/preview")
                        .file(csvFile("""
                                Datum,Betrag,Text
                                15.03.2025,-42.50,Coffee beans
                                16.03.2025,2500.00,Salary March
                                """))
                        .param("accountId", account.getId().toString())
                        .with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format", is("CSV")))
                .andExpect(jsonPath("$.totalRows", is(2)))
                .andExpect(jsonPath("$.newRows", is(2)))
                .andExpect(jsonPath("$.duplicates", is(0)))
                .andExpect(jsonPath("$.rows[0].description", is("Coffee beans")))
                .andExpect(jsonPath("$.rows[0].duplicate", is(false)))
                .andExpect(jsonPath("$.rows[0].suggestedCategoryId", is(groceries.getId().toString())))
                .andExpect(jsonPath("$.rows[0].suggestedCategoryName", is("Groceries")))
                .andExpect(jsonPath("$.rows[1].suggestedCategoryId", nullValue()));

        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void camt_preview_is_detected_by_filename_and_returns_signed_rows_with_references() throws Exception {
        mockMvc.perform(multipart("/api/v1/transactions/import/preview")
                        .file(camtFile(CAMT_XML))
                        .param("accountId", account.getId().toString())
                        .with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format", is("CAMT053")))
                .andExpect(jsonPath("$.totalRows", is(1)))
                .andExpect(jsonPath("$.rows[0].amount", is(-42.50)))
                .andExpect(jsonPath("$.rows[0].currency", is("CHF")))
                .andExpect(jsonPath("$.rows[0].date", is("2025-03-15")))
                .andExpect(jsonPath("$.rows[0].counterparty", is("Migros Testfiliale")))
                .andExpect(jsonPath("$.rows[0].externalRef", is("SYNTH-IT-REF-1")))
                .andExpect(jsonPath("$.rows[0].duplicate", is(false)));

        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void camt_preview_flags_rows_whose_external_reference_already_exists() throws Exception {
        transactionRepository.save(Transaction.builder()
                .userId(TEST_USER_ID)
                .amount(new BigDecimal("-42.50"))
                .currency("CHF")
                .date(LocalDate.of(2025, Month.MARCH, 15))
                .description("Einkauf Lebensmittel")
                .account(account)
                .source(TransactionSource.CAMT_IMPORT)
                .externalRef("SYNTH-IT-REF-1")
                .build());

        mockMvc.perform(multipart("/api/v1/transactions/import/preview")
                        .file(camtFile(CAMT_XML))
                        .param("accountId", account.getId().toString())
                        .with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicates", is(1)))
                .andExpect(jsonPath("$.newRows", is(0)))
                .andExpect(jsonPath("$.rows[0].duplicate", is(true)));

        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    void preview_into_a_foreign_account_returns_404() throws Exception {
        Account foreignAccount = accountRepository.save(Account.builder()
                .userId(OTHER_USER_ID)
                .name("Foreign Account")
                .type(AccountType.CHECKING)
                .currency("CHF")
                .initialBalance(BigDecimal.ZERO)
                .build());

        mockMvc.perform(multipart("/api/v1/transactions/import/preview")
                        .file(camtFile(CAMT_XML))
                        .param("accountId", foreignAccount.getId().toString())
                        .with(asUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    void preview_of_a_malformed_xml_file_returns_422() throws Exception {
        mockMvc.perform(multipart("/api/v1/transactions/import/preview")
                        .file(camtFile("<html><body>not a statement</body></html>"))
                        .param("accountId", account.getId().toString())
                        .with(asUser()))
                .andExpect(status().isUnprocessableContent());

        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void preview_without_authentication_returns_401() throws Exception {
        mockMvc.perform(multipart("/api/v1/transactions/import/preview")
                        .file(camtFile(CAMT_XML))
                        .param("accountId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }
}
