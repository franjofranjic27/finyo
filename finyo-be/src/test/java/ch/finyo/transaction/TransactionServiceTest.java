package ch.finyo.transaction;

import ch.finyo.account.Account;
import ch.finyo.account.AccountRepository;
import ch.finyo.account.AccountType;
import ch.finyo.category.Category;
import ch.finyo.category.CategoryRepository;
import ch.finyo.category.CategoryType;
import ch.finyo.common.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

/**
 * Pure unit tests for TransactionService.
 *
 * Complements TransactionEdgeCaseIT (end-to-end multi-tenancy) with fast
 * coverage of the service-level branching:
 *   1. getAll(): date-filtered vs. unfiltered repository query and page mapping.
 *   2. create()/update(): account and category ownership checks, optional
 *      category, CHF currency default, MANUAL source on create, source and
 *      currency preservation on update.
 *   3. delete(): ownership guard before deleteById.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final String USER_ID = "user-tx-1";
    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final LocalDate TX_DATE = LocalDate.of(2025, Month.MAY, 20);

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private TransactionService transactionService;

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private Account buildAccount() {
        return Account.builder()
                .id(ACCOUNT_ID)
                .userId(USER_ID)
                .name("Checking")
                .type(AccountType.CHECKING)
                .currency("CHF")
                .initialBalance(BigDecimal.ZERO)
                .build();
    }

    private Category buildCategory(UUID id) {
        return Category.builder()
                .id(id)
                .userId(USER_ID)
                .name("Groceries")
                .type(CategoryType.EXPENSE)
                .build();
    }

    private Transaction buildTransaction(UUID id, String amount) {
        return Transaction.builder()
                .id(id)
                .userId(USER_ID)
                .amount(new BigDecimal(amount))
                .currency("CHF")
                .date(TX_DATE)
                .description("Existing")
                .account(buildAccount())
                .source(TransactionSource.CSV_IMPORT)
                .build();
    }

    private TransactionRequest requestWith(String currency, UUID categoryId) {
        return new TransactionRequest(
                new BigDecimal("-50.00"), currency, TX_DATE, "Weekly shopping", categoryId, ACCOUNT_ID);
    }

    // =========================================================================
    // getAll()
    // =========================================================================

    @Test
    void getAll_without_dates_queries_by_user_only_and_maps_page_metadata() {
        Pageable pageable = PageRequest.of(0, 2);
        List<Transaction> content = List.of(
                buildTransaction(UUID.randomUUID(), "-10.00"),
                buildTransaction(UUID.randomUUID(), "-20.00"));
        given(transactionRepository.findByUserId(USER_ID, pageable))
                .willReturn(new PageImpl<>(content, pageable, 5));

        TransactionPageResponse result = transactionService.getAll(USER_ID, null, null, pageable);

        assertThat(result.content()).hasSize(2);
        assertThat(result.totalElements()).isEqualTo(5);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.number()).isZero();
        then(transactionRepository).should(never()).findByUserIdAndDateBetween(any(), any(), any(), any());
    }

    @Test
    void getAll_with_both_dates_uses_the_date_range_query() {
        Pageable pageable = PageRequest.of(0, 10);
        LocalDate from = LocalDate.of(2025, Month.MAY, 1);
        LocalDate to = LocalDate.of(2025, Month.MAY, 31);
        given(transactionRepository.findByUserIdAndDateBetween(USER_ID, from, to, pageable))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        TransactionPageResponse result = transactionService.getAll(USER_ID, from, to, pageable);

        assertThat(result.content()).isEmpty();
        then(transactionRepository).should().findByUserIdAndDateBetween(USER_ID, from, to, pageable);
    }

    @Test
    void getAll_with_only_one_date_falls_back_to_the_unfiltered_query() {
        Pageable pageable = PageRequest.of(0, 10);
        given(transactionRepository.findByUserId(USER_ID, pageable))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        transactionService.getAll(USER_ID, LocalDate.of(2025, Month.MAY, 1), null, pageable);

        then(transactionRepository).should().findByUserId(USER_ID, pageable);
    }

    // =========================================================================
    // getById()
    // =========================================================================

    @Test
    void getById_maps_the_transaction_with_account_details() {
        UUID id = UUID.randomUUID();
        given(transactionRepository.findByIdAndUserId(id, USER_ID))
                .willReturn(Optional.of(buildTransaction(id, "-42.00")));

        TransactionResponse result = transactionService.getById(id, USER_ID);

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.accountName()).isEqualTo("Checking");
    }

    @Test
    void getById_throws_ResourceNotFoundException_for_a_foreign_transaction() {
        UUID id = UUID.randomUUID();
        given(transactionRepository.findByIdAndUserId(id, "attacker")).willReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getById(id, "attacker"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Transaction");
    }

    // =========================================================================
    // create()
    // =========================================================================

    @Test
    void create_sets_userId_manual_source_and_defaults_currency_to_chf() {
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                .willReturn(Optional.of(buildAccount()));
        given(transactionRepository.save(any(Transaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse result = transactionService.create(requestWith(null, null), USER_ID);

        assertThat(result.currency()).isEqualTo("CHF");
        assertThat(result.source()).isEqualTo(TransactionSource.MANUAL);
        assertThat(result.categoryId()).isNull();
        then(transactionRepository).should().save(argThat(t -> USER_ID.equals(t.getUserId())));
        then(categoryRepository).shouldHaveNoInteractions();
    }

    @Test
    void create_links_the_category_when_it_belongs_to_the_user() {
        UUID categoryId = UUID.randomUUID();
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                .willReturn(Optional.of(buildAccount()));
        given(categoryRepository.findByIdAndUserId(categoryId, USER_ID))
                .willReturn(Optional.of(buildCategory(categoryId)));
        given(transactionRepository.save(any(Transaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse result = transactionService.create(requestWith("EUR", categoryId), USER_ID);

        assertThat(result.categoryId()).isEqualTo(categoryId);
        assertThat(result.currency()).isEqualTo("EUR");
    }

    @Test
    void create_throws_ResourceNotFoundException_when_account_belongs_to_another_user() {
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).willReturn(Optional.empty());
        TransactionRequest request = requestWith(null, null);

        assertThatThrownBy(() -> transactionService.create(request, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account");
        then(transactionRepository).should(never()).save(any());
    }

    @Test
    void create_throws_ResourceNotFoundException_when_category_belongs_to_another_user() {
        UUID foreignCategoryId = UUID.randomUUID();
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                .willReturn(Optional.of(buildAccount()));
        given(categoryRepository.findByIdAndUserId(foreignCategoryId, USER_ID)).willReturn(Optional.empty());
        TransactionRequest request = requestWith(null, foreignCategoryId);

        assertThatThrownBy(() -> transactionService.create(request, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category");
        then(transactionRepository).should(never()).save(any());
    }

    // =========================================================================
    // update()
    // =========================================================================

    @Test
    void update_preserves_the_original_source_and_falls_back_to_the_existing_currency() {
        UUID id = UUID.randomUUID();
        given(transactionRepository.findByIdAndUserId(id, USER_ID))
                .willReturn(Optional.of(buildTransaction(id, "-42.00")));
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID))
                .willReturn(Optional.of(buildAccount()));
        given(transactionRepository.save(any(Transaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse result = transactionService.update(id, requestWith(null, null), USER_ID);

        assertThat(result.source())
                .as("an imported transaction must stay CSV_IMPORT after a manual edit")
                .isEqualTo(TransactionSource.CSV_IMPORT);
        assertThat(result.currency()).isEqualTo("CHF");
        assertThat(result.amount()).isEqualByComparingTo("-50.00");
        assertThat(result.description()).isEqualTo("Weekly shopping");
    }

    @Test
    void update_throws_ResourceNotFoundException_when_transaction_is_not_found() {
        UUID id = UUID.randomUUID();
        given(transactionRepository.findByIdAndUserId(id, USER_ID)).willReturn(Optional.empty());
        TransactionRequest request = requestWith(null, null);

        assertThatThrownBy(() -> transactionService.update(id, request, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Transaction");
        then(transactionRepository).should(never()).save(any());
    }

    @Test
    void update_throws_ResourceNotFoundException_when_target_account_is_foreign() {
        UUID id = UUID.randomUUID();
        given(transactionRepository.findByIdAndUserId(id, USER_ID))
                .willReturn(Optional.of(buildTransaction(id, "-42.00")));
        given(accountRepository.findByIdAndUserId(ACCOUNT_ID, USER_ID)).willReturn(Optional.empty());
        TransactionRequest request = requestWith(null, null);

        assertThatThrownBy(() -> transactionService.update(id, request, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account");
        then(transactionRepository).should(never()).save(any());
    }

    // =========================================================================
    // delete()
    // =========================================================================

    @Test
    void delete_removes_the_transaction_when_it_belongs_to_the_user() {
        UUID id = UUID.randomUUID();
        given(transactionRepository.findByIdAndUserId(id, USER_ID))
                .willReturn(Optional.of(buildTransaction(id, "-1.00")));

        transactionService.delete(id, USER_ID);

        then(transactionRepository).should().deleteById(id);
    }

    @Test
    void delete_never_calls_deleteById_for_a_foreign_transaction() {
        UUID id = UUID.randomUUID();
        given(transactionRepository.findByIdAndUserId(id, "attacker")).willReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.delete(id, "attacker"))
                .isInstanceOf(ResourceNotFoundException.class);
        then(transactionRepository).should(never()).deleteById(any());
    }
}
