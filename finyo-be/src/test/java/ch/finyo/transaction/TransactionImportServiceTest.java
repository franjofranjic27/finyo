package ch.finyo.transaction;

import ch.finyo.account.Account;
import ch.finyo.account.AccountRepository;
import ch.finyo.account.AccountType;
import ch.finyo.category.Category;
import ch.finyo.category.CategoryRepository;
import ch.finyo.category.CategoryRule;
import ch.finyo.category.CategoryRuleMatcher;
import ch.finyo.category.CategoryRuleService;
import ch.finyo.category.CategoryType;
import ch.finyo.common.DocumentProcessingException;
import ch.finyo.common.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.times;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for the two-step statement import (no Spring context, no
 * database): format detection, duplicate flagging via external reference and
 * via the date/amount/description heuristic, rule-based category suggestions,
 * commit skip counting, resource limits and tenant isolation for accounts and
 * categories.
 *
 * Uses a real CsvImportService and a real CamtParser (both pure) so preview
 * covers actual parsing instead of stubbed row values.
 */
@ExtendWith(MockitoExtension.class)
class TransactionImportServiceTest {

    private static final String USER_ID = "user-import-2";
    private static final UUID ACCOUNT_ID = UUID.randomUUID();

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryRuleService categoryRuleService;

    @Captor
    private ArgumentCaptor<List<Transaction>> savedTransactions;

    private TransactionImportService importService;

    @BeforeEach
    void createService() {
        CsvImportService csvImportService =
                new CsvImportService(transactionRepository, accountRepository, categoryRuleService);
        importService = new TransactionImportService(transactionRepository, accountRepository,
                categoryRepository, categoryRuleService, csvImportService, new CamtParser());
        lenient().when(categoryRuleService.loadMatcher(USER_ID)).thenReturn(CategoryRuleMatcher.empty());
    }

    // -------------------------------------------------------------------------
    // Builders
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
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).willReturn(Optional.of(buildAccount()));
    }

    private Category buildCategory(String name) {
        return Category.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .name(name)
                .type(CategoryType.EXPENSE)
                .build();
    }

    private CategoryRuleMatcher matcherWithRule(String keyword, Category category) {
        return CategoryRuleMatcher.of(List.of(CategoryRule.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .keyword(keyword)
                .category(category)
                .build()));
    }

    /** Default CSV params: custom mapping date=0, amount=1, description=2, no header. */
    private ImportRequest csvParams() {
        return new ImportRequest(ACCOUNT_ID, null, 0, 1, 2, null, null, null, false, true);
    }

    private MockMultipartFile file(String name, String contentType, String content) {
        return new MockMultipartFile("file", name, contentType, content.getBytes(StandardCharsets.UTF_8));
    }

    private static final String CAMT_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.04">
              <BkToCstmrStmt><Stmt>
                <Ntry>
                  <Amt Ccy="CHF">42.50</Amt>
                  <CdtDbtInd>DBIT</CdtDbtInd>
                  <Sts>BOOK</Sts>
                  <BookgDt><Dt>2025-03-15</Dt></BookgDt>
                  <AcctSvcrRef>SYNTH-REF-1</AcctSvcrRef>
                  <NtryDtls><TxDtls>
                    <RltdPties><Cdtr><Nm>Migros Testfiliale</Nm></Cdtr></RltdPties>
                    <RmtInf><Ustrd>Einkauf</Ustrd></RmtInf>
                  </TxDtls></NtryDtls>
                </Ntry>
              </Stmt></BkToCstmrStmt>
            </Document>
            """;

    // =========================================================================
    // Format detection
    // =========================================================================

    @Test
    void detect_recognizes_camt_by_extension_and_by_xml_head() {
        assertThat(ImportFormat.detect("statement.XML", null)).isEqualTo(ImportFormat.CAMT053);
        assertThat(ImportFormat.detect("statement.txt", "<?xml version=\"1.0\"?>".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(ImportFormat.CAMT053);
        assertThat(ImportFormat.detect(null, "  <Document xmlns=".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(ImportFormat.CAMT053);
    }

    @Test
    void detect_recognizes_excel_by_extension_and_by_zip_magic() {
        assertThat(ImportFormat.detect("export.xlsx", null)).isEqualTo(ImportFormat.EXCEL);
        assertThat(ImportFormat.detect("export.bin", new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00}))
                .isEqualTo(ImportFormat.EXCEL);
    }

    @Test
    void detect_falls_back_to_csv() {
        assertThat(ImportFormat.detect("export.csv", "Datum,Betrag\n".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(ImportFormat.CSV);
        assertThat(ImportFormat.detect(null, null)).isEqualTo(ImportFormat.CSV);
    }

    // =========================================================================
    // preview()
    // =========================================================================

    @Test
    void preview_detects_camt_and_flags_duplicates_via_the_external_reference() throws IOException {
        givenAccountExists();
        given(transactionRepository.findExistingExternalRefs(USER_ID, Set.of("SYNTH-REF-1")))
                .willReturn(List.of("SYNTH-REF-1"));
        MockMultipartFile camtFile = file("statement.xml", "application/xml", CAMT_XML);

        ImportPreviewResponse response = importService.preview(camtFile, ACCOUNT_ID, csvParams(), null, USER_ID);

        assertThat(response.format()).isEqualTo(ImportFormat.CAMT053);
        assertThat(response.totalRows()).isEqualTo(1);
        assertThat(response.duplicates()).isEqualTo(1);
        assertThat(response.newRows()).isZero();
        ImportPreviewRow row = response.rows().get(0);
        assertThat(row.duplicate()).isTrue();
        assertThat(row.amount()).isEqualByComparingTo("-42.50");
        assertThat(row.counterparty()).isEqualTo("Migros Testfiliale");
        assertThat(row.externalRef()).isEqualTo("SYNTH-REF-1");
        // rows with a reference never need the heuristic query
        then(transactionRepository).should(never())
                .findFingerprintsByUserIdAndDateBetween(any(), any(), any());
        then(transactionRepository).should(never()).saveAll(any());
    }

    @Test
    void preview_flags_csv_duplicates_via_the_date_amount_description_heuristic() throws IOException {
        givenAccountExists();
        // one range query for the whole batch instead of one query per row
        given(transactionRepository.findFingerprintsByUserIdAndDateBetween(
                USER_ID, LocalDate.of(2025, Month.MARCH, 15), LocalDate.of(2025, Month.MARCH, 16)))
                .willReturn(List.of(new TransactionFingerprint(
                        LocalDate.of(2025, Month.MARCH, 15), new BigDecimal("-42.5000"), "Coffee")));
        MockMultipartFile csvFile = file("import.csv", "text/csv",
                "15.03.2025,-42.50,Coffee\n16.03.2025,-10.00,Tea\n");

        ImportPreviewResponse response = importService.preview(csvFile, ACCOUNT_ID, csvParams(), null, USER_ID);

        assertThat(response.format()).isEqualTo(ImportFormat.CSV);
        assertThat(response.totalRows()).isEqualTo(2);
        assertThat(response.duplicates()).isEqualTo(1);
        assertThat(response.newRows()).isEqualTo(1);
        assertThat(response.rows().get(0).duplicate()).isTrue();
        assertThat(response.rows().get(1).duplicate()).isFalse();
        assertThat(response.rows().get(1).externalRef()).isNull();
        then(transactionRepository).should(times(1))
                .findFingerprintsByUserIdAndDateBetween(any(), any(), any());
        then(transactionRepository).should(never()).findExistingExternalRefs(any(), any());
    }

    @Test
    void preview_rejects_tabular_files_beyond_the_row_cap() {
        givenAccountExists();
        String tooManyRows = "15.03.2025,-1.00,row\n".repeat(10_001);
        MockMultipartFile csvFile = file("import.csv", "text/csv", tooManyRows);
        ImportRequest params = csvParams();

        assertThatThrownBy(() -> importService.preview(csvFile, ACCOUNT_ID, params, null, USER_ID))
                .isInstanceOf(DocumentProcessingException.class)
                .hasMessageContaining("maximum");
    }

    @Test
    void preview_wraps_a_wrong_format_override_into_a_processing_error() {
        givenAccountExists();
        // client-supplied override: forcing EXCEL onto XML bytes must not surface as a 500
        MockMultipartFile camtFile = file("statement.xml", "application/xml", CAMT_XML);
        ImportRequest params = csvParams();

        assertThatThrownBy(() -> importService.preview(camtFile, ACCOUNT_ID, params, ImportFormat.EXCEL, USER_ID))
                .isInstanceOf(DocumentProcessingException.class)
                .hasMessageContaining("Excel workbook");
    }

    @Test
    void preview_applies_category_suggestions_from_the_rule_matcher() throws IOException {
        givenAccountExists();
        Category groceries = buildCategory("Groceries");
        // keyword only hits the counterparty, proving description+counterparty matching
        given(categoryRuleService.loadMatcher(USER_ID)).willReturn(matcherWithRule("migros", groceries));
        MockMultipartFile camtFile = file("statement.xml", "application/xml", CAMT_XML);

        ImportPreviewResponse response = importService.preview(camtFile, ACCOUNT_ID, csvParams(), null, USER_ID);

        ImportPreviewRow row = response.rows().get(0);
        assertThat(row.suggestedCategoryId()).isEqualTo(groceries.getId());
        assertThat(row.suggestedCategoryName()).isEqualTo("Groceries");
    }

    @Test
    void preview_honors_the_format_override_over_detection() throws IOException {
        givenAccountExists();
        // .xml filename, but forced CSV: content parses as one CSV row
        MockMultipartFile trickyFile = file("statement.xml", "text/csv", "15.03.2025,-1.00,Forced csv\n");

        ImportPreviewResponse response =
                importService.preview(trickyFile, ACCOUNT_ID, csvParams(), ImportFormat.CSV, USER_ID);

        assertThat(response.format()).isEqualTo(ImportFormat.CSV);
        assertThat(response.rows()).hasSize(1);
    }

    @Test
    void preview_counts_unparseable_csv_rows_as_failed_with_error_entries() throws IOException {
        givenAccountExists();
        MockMultipartFile csvFile = file("import.csv", "text/csv",
                "not-a-date,-10.00,bad row\n15.03.2025,-20.00,good row\n");

        ImportPreviewResponse response = importService.preview(csvFile, ACCOUNT_ID, csvParams(), null, USER_ID);

        assertThat(response.failed()).isEqualTo(1);
        assertThat(response.totalRows()).isEqualTo(2);
        assertThat(response.rows()).hasSize(1);
        assertThat(response.errors()).hasSize(1);
        assertThat(response.errors().get(0)).startsWith("Row 1:");
    }

    @Test
    void preview_for_a_foreign_account_throws_not_found() {
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).willReturn(Optional.empty());
        MockMultipartFile camtFile = file("statement.xml", "application/xml", CAMT_XML);
        ImportRequest params = csvParams();

        assertThatThrownBy(() -> importService.preview(camtFile, ACCOUNT_ID, params, null, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account");
    }

    // =========================================================================
    // commit()
    // =========================================================================

    private ImportCommitRow commitRow(String date, String amount, String description,
                                      String externalRef, UUID categoryId) {
        return new ImportCommitRow(LocalDate.parse(date), new BigDecimal(amount), null,
                description, externalRef, categoryId);
    }

    @Test
    void commit_persists_rows_with_source_category_and_external_reference() {
        givenAccountExists();
        Category groceries = buildCategory("Groceries");
        given(categoryRepository.findByIdAndUserId(groceries.getId(), USER_ID)).willReturn(Optional.of(groceries));
        given(transactionRepository.findExistingExternalRefs(USER_ID, Set.of("SYNTH-REF-9")))
                .willReturn(List.of());

        ImportResultResponse result = importService.commit(new ImportCommitRequest(
                ACCOUNT_ID, ImportFormat.CAMT053, true,
                List.of(commitRow("2025-03-15", "-42.50", "Einkauf", "SYNTH-REF-9", groceries.getId()))), USER_ID);

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.skippedDuplicates()).isZero();
        then(transactionRepository).should().saveAll(savedTransactions.capture());
        Transaction saved = savedTransactions.getValue().get(0);
        assertThat(saved.getSource()).isEqualTo(TransactionSource.CAMT_IMPORT);
        assertThat(saved.getExternalRef()).isEqualTo("SYNTH-REF-9");
        assertThat(saved.getCategory()).isEqualTo(groceries);
        assertThat(saved.getCurrency()).isEqualTo("CHF");
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void commit_imports_every_batch_sub_transaction_without_a_reference() {
        // Sammelbuchung: the parser leaves sub-transactions without an own reference at null, so
        // they must each be persisted — they used to collapse into one "in-batch duplicate".
        givenAccountExists();
        given(transactionRepository.findFingerprintsByUserIdAndDateBetween(
                USER_ID, LocalDate.of(2025, Month.JUNE, 1), LocalDate.of(2025, Month.JUNE, 1)))
                .willReturn(List.of());

        ImportResultResponse result = importService.commit(new ImportCommitRequest(
                ACCOUNT_ID, ImportFormat.CAMT053, true,
                List.of(
                        commitRow("2025-06-01", "-25.00", "Lastschrift eins", null, null),
                        commitRow("2025-06-01", "-30.00", "Lastschrift zwei", null, null))), USER_ID);

        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.skippedDuplicates()).isZero();
        then(transactionRepository).should().saveAll(savedTransactions.capture());
        assertThat(savedTransactions.getValue())
                .extracting(Transaction::getDescription)
                .containsExactly("Lastschrift eins", "Lastschrift zwei");
    }

    @Test
    void commit_skips_duplicates_by_reference_and_guards_against_repeated_references_in_one_batch() {
        givenAccountExists();
        given(transactionRepository.findExistingExternalRefs(USER_ID, Set.of("EXISTING-REF", "NEW-REF")))
                .willReturn(List.of("EXISTING-REF"));

        ImportResultResponse result = importService.commit(new ImportCommitRequest(
                ACCOUNT_ID, ImportFormat.CAMT053, true,
                List.of(
                        commitRow("2025-03-15", "-1.00", "existing", "EXISTING-REF", null),
                        commitRow("2025-03-16", "-2.00", "new", "NEW-REF", null),
                        commitRow("2025-03-16", "-3.00", "same ref again", "NEW-REF", null))), USER_ID);

        assertThat(result.totalRows()).isEqualTo(3);
        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.skippedDuplicates()).isEqualTo(2);
        then(transactionRepository).should().saveAll(savedTransactions.capture());
        assertThat(savedTransactions.getValue())
                .hasSize(1)
                .allSatisfy(t -> assertThat(t.getExternalRef()).isEqualTo("NEW-REF"));
    }

    @Test
    void commit_uses_csv_import_source_and_the_heuristic_duplicate_check_without_references() {
        givenAccountExists();
        given(transactionRepository.findFingerprintsByUserIdAndDateBetween(
                USER_ID, LocalDate.of(2025, Month.MARCH, 15), LocalDate.of(2025, Month.MARCH, 15)))
                .willReturn(List.of(new TransactionFingerprint(
                        LocalDate.of(2025, Month.MARCH, 15), new BigDecimal("-42.5000"), "Coffee")));

        ImportResultResponse result = importService.commit(new ImportCommitRequest(
                ACCOUNT_ID, ImportFormat.CSV, true,
                List.of(commitRow("2025-03-15", "-42.50", "Coffee", null, null))), USER_ID);

        assertThat(result.imported()).isZero();
        assertThat(result.skippedDuplicates()).isEqualTo(1);
        then(transactionRepository).should().saveAll(List.of());
    }

    @Test
    void commit_imports_heuristic_duplicates_when_skipping_is_disabled() {
        givenAccountExists();
        given(transactionRepository.findFingerprintsByUserIdAndDateBetween(
                USER_ID, LocalDate.of(2025, Month.MARCH, 15), LocalDate.of(2025, Month.MARCH, 15)))
                .willReturn(List.of(new TransactionFingerprint(
                        LocalDate.of(2025, Month.MARCH, 15), new BigDecimal("-42.5000"), "Coffee")));

        ImportResultResponse result = importService.commit(new ImportCommitRequest(
                ACCOUNT_ID, ImportFormat.CSV, false,
                List.of(commitRow("2025-03-15", "-42.50", "Coffee", null, null))), USER_ID);

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.skippedDuplicates()).isZero();
    }

    @Test
    void commit_always_skips_rows_whose_reference_is_already_stored_even_without_skip_duplicates() {
        // The partial unique index on (user_id, external_ref) would reject the insert and roll the
        // whole batch back with a 409 — a known reference can never be re-imported.
        givenAccountExists();
        given(transactionRepository.findExistingExternalRefs(USER_ID, Set.of("EXISTING-REF")))
                .willReturn(List.of("EXISTING-REF"));

        ImportResultResponse result = importService.commit(new ImportCommitRequest(
                ACCOUNT_ID, ImportFormat.CAMT053, false,
                List.of(commitRow("2025-03-15", "-1.00", "existing", "EXISTING-REF", null))), USER_ID);

        assertThat(result.imported()).isZero();
        assertThat(result.skippedDuplicates()).isEqualTo(1);
        then(transactionRepository).should().saveAll(List.of());
    }

    @Test
    void commit_with_a_foreign_account_throws_not_found_and_persists_nothing() {
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).willReturn(Optional.empty());
        ImportCommitRequest request = new ImportCommitRequest(ACCOUNT_ID, ImportFormat.CSV, true,
                List.of(commitRow("2025-03-15", "-1.00", "x", null, null)));

        assertThatThrownBy(() -> importService.commit(request, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account");
        then(transactionRepository).should(never()).saveAll(any());
    }

    @Test
    void commit_with_a_foreign_category_throws_not_found_and_persists_nothing() {
        givenAccountExists();
        UUID foreignCategoryId = UUID.randomUUID();
        given(categoryRepository.findByIdAndUserId(foreignCategoryId, USER_ID)).willReturn(Optional.empty());
        ImportCommitRequest request = new ImportCommitRequest(ACCOUNT_ID, ImportFormat.CSV, true,
                List.of(commitRow("2025-03-15", "-1.00", "x", null, foreignCategoryId)));

        assertThatThrownBy(() -> importService.commit(request, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category");
        then(transactionRepository).should(never()).saveAll(any());
    }
}
