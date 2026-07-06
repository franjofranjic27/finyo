package ch.finyo.account;

import ch.finyo.common.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
 * Pure unit tests for AccountService.
 *
 * No Spring context is started. The AccountRepository is mocked via Mockito
 * so tests remain fast and deterministic.
 *
 * Key concerns exercised:
 *   1. Multi-tenant scoping — every query must be filtered by userId.
 *   2. ResourceNotFoundException is thrown (not swallowed) when a resource
 *      does not exist or belongs to a different user.
 *   3. The service sets the correct userId on entities it creates / updates.
 *   4. delete() must NOT call deleteById() if the entity does not exist.
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    // -------------------------------------------------------------------------
    // Helpers to build test entities without JPA infrastructure
    // -------------------------------------------------------------------------

    private Account buildAccount(UUID id, String userId, String name) {
        return Account.builder()
                .id(id)
                .userId(userId)
                .name(name)
                .type(AccountType.CHECKING)
                .currency("CHF")
                .initialBalance(BigDecimal.ZERO)
                .build();
    }

    // =========================================================================
    // getAll()
    // =========================================================================

    @Test
    void getAll_returns_accounts_for_user_in_ascending_name_order() {
        String userId = "user-1";
        Account alpha = buildAccount(UUID.randomUUID(), userId, "Alpha Account");
        Account beta  = buildAccount(UUID.randomUUID(), userId, "Beta Account");
        given(accountRepository.findByUserIdOrderByNameAsc(userId))
                .willReturn(List.of(alpha, beta));

        List<AccountResponse> result = accountService.getAll(userId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Alpha Account");
        assertThat(result.get(1).name()).isEqualTo("Beta Account");
    }

    @Test
    void getAll_returns_empty_list_when_user_has_no_accounts() {
        given(accountRepository.findByUserIdOrderByNameAsc("new-user")).willReturn(List.of());

        List<AccountResponse> result = accountService.getAll("new-user");

        assertThat(result).isEmpty();
    }

    @Test
    void getAll_only_queries_by_userId_never_by_plain_findAll() {
        // Ensures the repository method that scopes by userId is used, not findAll()
        String userId = "user-scoped";
        given(accountRepository.findByUserIdOrderByNameAsc(userId)).willReturn(List.of());

        accountService.getAll(userId);

        then(accountRepository).should().findByUserIdOrderByNameAsc(userId);
        then(accountRepository).shouldHaveNoMoreInteractions();
    }

    // =========================================================================
    // getById()
    // =========================================================================

    @Test
    void getById_returns_account_when_it_belongs_to_user() {
        UUID id = UUID.randomUUID();
        String userId = "user-1";
        Account account = buildAccount(id, userId, "My Savings");
        given(accountRepository.findByIdAndUserId(id, userId)).willReturn(Optional.of(account));

        AccountResponse result = accountService.getById(id, userId);

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.name()).isEqualTo("My Savings");
    }

    @Test
    void getById_throws_ResourceNotFoundException_when_account_does_not_exist() {
        UUID id = UUID.randomUUID();
        given(accountRepository.findByIdAndUserId(id, "user-1")).willReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getById(id, "user-1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account")
                .hasMessageContaining(id.toString());
    }

    @Test
    void getById_throws_when_account_belongs_to_different_user() {
        // Critical multi-tenant isolation test:
        // findByIdAndUserId() returns empty when the userId does not match,
        // meaning cross-tenant access is blocked at the repository level.
        UUID id = UUID.randomUUID();
        given(accountRepository.findByIdAndUserId(id, "wrong-user")).willReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getById(id, "wrong-user"))
                .as("Account owned by a different user must not be accessible — must throw, not return data")
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // =========================================================================
    // create()
    // =========================================================================

    @Test
    void create_sets_userId_on_the_persisted_account() {
        String userId = "user-42";
        AccountRequest request = new AccountRequest("Savings", AccountType.SAVINGS, "CHF", BigDecimal.TEN, null);
        Account savedAccount = buildAccount(UUID.randomUUID(), userId, "Savings");
        given(accountRepository.save(any(Account.class))).willReturn(savedAccount);

        accountService.create(request, userId);

        then(accountRepository).should().save(argThat(a ->
                userId.equals(a.getUserId())));
    }

    @Test
    void create_returns_response_with_the_correct_name() {
        String userId = "user-42";
        AccountRequest request = new AccountRequest("Travel Fund", AccountType.SAVINGS, "CHF", BigDecimal.ZERO, null);
        Account savedAccount = buildAccount(UUID.randomUUID(), userId, "Travel Fund");
        given(accountRepository.save(any(Account.class))).willReturn(savedAccount);

        AccountResponse result = accountService.create(request, userId);

        assertThat(result.name()).isEqualTo("Travel Fund");
    }

    @Test
    void create_defaults_currency_to_chf_when_not_provided() {
        // AccountRequest.currency is @NotBlank but the service still defensively
        // coerces null to "CHF". Simulate a null by creating a request with null.
        String userId = "user-x";
        AccountRequest request = new AccountRequest("Misc", AccountType.OTHER, null, null, null);
        Account savedAccount = Account.builder()
                .id(UUID.randomUUID()).userId(userId).name("Misc")
                .type(AccountType.OTHER).currency("CHF").initialBalance(BigDecimal.ZERO)
                .build();
        given(accountRepository.save(any(Account.class))).willReturn(savedAccount);

        accountService.create(request, userId);

        // The account passed to save() must have currency "CHF"
        then(accountRepository).should().save(argThat(a ->
                "CHF".equals(a.getCurrency())));
    }

    @Test
    void create_defaults_initialBalance_to_zero_when_not_provided() {
        String userId = "user-x";
        AccountRequest request = new AccountRequest("Misc", AccountType.OTHER, "CHF", null, null);
        Account savedAccount = Account.builder()
                .id(UUID.randomUUID()).userId(userId).name("Misc")
                .type(AccountType.OTHER).currency("CHF").initialBalance(BigDecimal.ZERO)
                .build();
        given(accountRepository.save(any(Account.class))).willReturn(savedAccount);

        accountService.create(request, userId);

        then(accountRepository).should().save(argThat(a ->
                BigDecimal.ZERO.compareTo(a.getInitialBalance()) == 0));
    }

    @Test
    void create_preserves_the_provided_color() {
        String userId = "user-x";
        AccountRequest request = new AccountRequest("Visa", AccountType.CREDIT_CARD, "CHF", BigDecimal.ZERO, "#FF5733");
        Account savedAccount = Account.builder()
                .id(UUID.randomUUID()).userId(userId).name("Visa")
                .type(AccountType.CREDIT_CARD).currency("CHF").initialBalance(BigDecimal.ZERO)
                .color("#FF5733")
                .build();
        given(accountRepository.save(any(Account.class))).willReturn(savedAccount);

        AccountResponse result = accountService.create(request, userId);

        assertThat(result.color()).isEqualTo("#FF5733");
        then(accountRepository).should().save(argThat(a ->
                "#FF5733".equals(a.getColor())));
    }

    // =========================================================================
    // update()
    // =========================================================================

    @Test
    void update_throws_ResourceNotFoundException_when_account_not_found() {
        UUID id = UUID.randomUUID();
        AccountRequest request = new AccountRequest("New Name", AccountType.SAVINGS, "CHF", BigDecimal.ZERO, null);
        given(accountRepository.findByIdAndUserId(id, "user-1")).willReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.update(id, request, "user-1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account");
    }

    @Test
    void update_throws_when_account_belongs_to_different_user() {
        UUID id = UUID.randomUUID();
        AccountRequest request = new AccountRequest("Hack", AccountType.OTHER, "CHF", BigDecimal.ZERO, null);
        given(accountRepository.findByIdAndUserId(id, "attacker-user")).willReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.update(id, request, "attacker-user"))
                .as("Update must be scoped to the authenticated user — cannot update another user's account")
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_persists_new_name_from_request() {
        UUID id = UUID.randomUUID();
        String userId = "user-upd";
        Account existing = buildAccount(id, userId, "Old Name");
        Account saved = buildAccount(id, userId, "New Name");

        given(accountRepository.findByIdAndUserId(id, userId)).willReturn(Optional.of(existing));
        given(accountRepository.save(any(Account.class))).willReturn(saved);

        AccountResponse result = accountService.update(id, new AccountRequest("New Name", AccountType.CHECKING, "CHF", BigDecimal.ZERO, null), userId);

        assertThat(result.name()).isEqualTo("New Name");
        then(accountRepository).should().save(argThat(a ->
                "New Name".equals(a.getName())));
    }

    @Test
    void update_retains_existing_currency_when_request_currency_is_null() {
        UUID id = UUID.randomUUID();
        String userId = "user-upd";
        Account existing = Account.builder()
                .id(id).userId(userId).name("My Account")
                .type(AccountType.CHECKING).currency("EUR").initialBalance(BigDecimal.ZERO)
                .build();
        Account saved = Account.builder()
                .id(id).userId(userId).name("My Account")
                .type(AccountType.SAVINGS).currency("EUR").initialBalance(BigDecimal.ZERO)
                .build();

        given(accountRepository.findByIdAndUserId(id, userId)).willReturn(Optional.of(existing));
        given(accountRepository.save(any(Account.class))).willReturn(saved);

        accountService.update(id, new AccountRequest("My Account", AccountType.SAVINGS, null, null, null), userId);

        then(accountRepository).should().save(argThat(a ->
                "EUR".equals(a.getCurrency())));
    }

    // =========================================================================
    // delete()
    // =========================================================================

    @Test
    void delete_removes_the_account_when_it_belongs_to_user() {
        UUID id = UUID.randomUUID();
        given(accountRepository.existsByIdAndUserId(id, "user-1")).willReturn(true);

        accountService.delete(id, "user-1");

        then(accountRepository).should().deleteById(id);
    }

    @Test
    void delete_throws_ResourceNotFoundException_when_account_not_found() {
        UUID id = UUID.randomUUID();
        given(accountRepository.existsByIdAndUserId(id, "user-1")).willReturn(false);

        assertThatThrownBy(() -> accountService.delete(id, "user-1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account");
    }

    @Test
    void delete_never_calls_deleteById_when_account_does_not_exist() {
        // Critical safety guard: if the existence check fails we must NOT
        // proceed to delete, as the UUID might belong to another user.
        UUID id = UUID.randomUUID();
        given(accountRepository.existsByIdAndUserId(id, "user-1")).willReturn(false);

        try {
            accountService.delete(id, "user-1");
        } catch (ResourceNotFoundException _) {
            // expected — the guard below verifies no delete happened
        }

        then(accountRepository).should(never()).deleteById(any());
    }

    @Test
    void delete_throws_when_account_belongs_to_different_user() {
        // existsByIdAndUserId returns false for the wrong user — same as not found
        UUID id = UUID.randomUUID();
        given(accountRepository.existsByIdAndUserId(id, "attacker")).willReturn(false);

        assertThatThrownBy(() -> accountService.delete(id, "attacker"))
                .as("delete() must not allow cross-user deletion — attacker must receive ResourceNotFoundException")
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
