package ch.finyo.savings;

import ch.finyo.account.Account;
import ch.finyo.account.AccountRepository;
import ch.finyo.account.AccountType;
import ch.finyo.common.ResourceNotFoundException;
import ch.finyo.common.SwissTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

/**
 * Pure unit tests for SavingsGoalService.
 *
 * Focus areas:
 *   1. Multi-tenant scoping via findByIdAndUserId (foreign goals → 404 semantics).
 *   2. Derived response fields: progressPercentage (zero target, capped at 100)
 *      and monthlyRequired (no target date, past date, already reached, normal).
 *      Date-dependent cases use dates relative to "today" in SwissTime.ZONE and
 *      month-start anchoring, so they are deterministic across the month.
 *   3. Account resolution: optional, but when given it must belong to the user.
 *   4. update() preserves currentAmount/archived, archive() flips only the flag.
 */
@ExtendWith(MockitoExtension.class)
class SavingsGoalServiceTest {

    private static final String USER_ID = "user-sg-1";

    @Mock
    private SavingsGoalRepository savingsGoalRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private SavingsGoalService savingsGoalService;

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private SavingsGoal.SavingsGoalBuilder goalBuilder() {
        return SavingsGoal.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .name("Emergency Fund")
                .targetAmount(new BigDecimal("10000"))
                .currentAmount(new BigDecimal("2500"))
                .archived(false);
    }

    private SavingsGoalRequest requestWith(BigDecimal currentAmount, UUID accountId) {
        return new SavingsGoalRequest(
                "Emergency Fund", new BigDecimal("10000"), currentAmount, null, accountId, "🏦", "#10b981");
    }

    private Account buildAccount(UUID id, String name) {
        return Account.builder()
                .id(id)
                .userId(USER_ID)
                .name(name)
                .type(AccountType.SAVINGS)
                .currency("CHF")
                .initialBalance(BigDecimal.ZERO)
                .build();
    }

    // =========================================================================
    // getAll() / getAllIncludingArchived()
    // =========================================================================

    @Test
    void getAll_maps_active_goals_to_responses() {
        given(savingsGoalRepository.findByUserIdAndArchivedFalseOrderByTargetDateAscNameAsc(USER_ID))
                .willReturn(List.of(goalBuilder().build()));

        List<SavingsGoalResponse> result = savingsGoalService.getAll(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Emergency Fund");
        assertThat(result.get(0).archived()).isFalse();
    }

    @Test
    void getAll_returns_empty_list_when_user_has_no_goals() {
        given(savingsGoalRepository.findByUserIdAndArchivedFalseOrderByTargetDateAscNameAsc(USER_ID))
                .willReturn(List.of());

        assertThat(savingsGoalService.getAll(USER_ID)).isEmpty();
    }

    @Test
    void getAllIncludingArchived_uses_the_repository_method_that_includes_archived_goals() {
        given(savingsGoalRepository.findByUserIdOrderByArchivedAscNameAsc(USER_ID))
                .willReturn(List.of(goalBuilder().archived(true).build()));

        List<SavingsGoalResponse> result = savingsGoalService.getAllIncludingArchived(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).archived()).isTrue();
        then(savingsGoalRepository).should().findByUserIdOrderByArchivedAscNameAsc(USER_ID);
    }

    // =========================================================================
    // getById()
    // =========================================================================

    @Test
    void getById_returns_goal_with_account_details_when_linked() {
        UUID goalId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        SavingsGoal goal = goalBuilder()
                .id(goalId)
                .account(buildAccount(accountId, "My Savings"))
                .build();
        given(savingsGoalRepository.findByIdAndUserId(goalId, USER_ID)).willReturn(Optional.of(goal));

        SavingsGoalResponse result = savingsGoalService.getById(goalId, USER_ID);

        assertThat(result.id()).isEqualTo(goalId);
        assertThat(result.accountId()).isEqualTo(accountId);
        assertThat(result.accountName()).isEqualTo("My Savings");
    }

    @Test
    void getById_throws_ResourceNotFoundException_when_goal_belongs_to_another_user() {
        UUID goalId = UUID.randomUUID();
        given(savingsGoalRepository.findByIdAndUserId(goalId, "attacker")).willReturn(Optional.empty());

        assertThatThrownBy(() -> savingsGoalService.getById(goalId, "attacker"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("SavingsGoal");
    }

    // =========================================================================
    // create()
    // =========================================================================

    @Test
    void create_sets_userId_and_defaults_currentAmount_to_zero() {
        given(savingsGoalRepository.save(any(SavingsGoal.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        SavingsGoalResponse result = savingsGoalService.create(requestWith(null, null), USER_ID);

        assertThat(result.currentAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        then(savingsGoalRepository).should().save(argThat(g ->
                USER_ID.equals(g.getUserId()) && !g.isArchived()));
    }

    @Test
    void create_links_the_account_when_it_belongs_to_the_user() {
        UUID accountId = UUID.randomUUID();
        given(accountRepository.findByIdAndUserId(accountId, USER_ID))
                .willReturn(Optional.of(buildAccount(accountId, "Linked")));
        given(savingsGoalRepository.save(any(SavingsGoal.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        SavingsGoalResponse result = savingsGoalService.create(requestWith(BigDecimal.ZERO, accountId), USER_ID);

        assertThat(result.accountId()).isEqualTo(accountId);
    }

    @Test
    void create_throws_ResourceNotFoundException_when_account_belongs_to_another_user() {
        UUID accountId = UUID.randomUUID();
        given(accountRepository.findByIdAndUserId(accountId, USER_ID)).willReturn(Optional.empty());
        SavingsGoalRequest request = requestWith(BigDecimal.ZERO, accountId);

        assertThatThrownBy(() -> savingsGoalService.create(request, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account");
        then(savingsGoalRepository).should(never()).save(any());
    }

    @Test
    void create_never_touches_account_repository_when_no_account_is_given() {
        given(savingsGoalRepository.save(any(SavingsGoal.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        savingsGoalService.create(requestWith(BigDecimal.ZERO, null), USER_ID);

        then(accountRepository).shouldHaveNoInteractions();
    }

    // =========================================================================
    // update()
    // =========================================================================

    @Test
    void update_throws_ResourceNotFoundException_when_goal_is_not_found() {
        UUID goalId = UUID.randomUUID();
        given(savingsGoalRepository.findByIdAndUserId(goalId, USER_ID)).willReturn(Optional.empty());
        SavingsGoalRequest request = requestWith(BigDecimal.ZERO, null);

        assertThatThrownBy(() -> savingsGoalService.update(goalId, request, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("SavingsGoal");
    }

    @Test
    void update_keeps_existing_currentAmount_when_request_omits_it() {
        UUID goalId = UUID.randomUUID();
        SavingsGoal existing = goalBuilder().id(goalId).currentAmount(new BigDecimal("2500")).build();
        given(savingsGoalRepository.findByIdAndUserId(goalId, USER_ID)).willReturn(Optional.of(existing));
        given(savingsGoalRepository.save(any(SavingsGoal.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        SavingsGoalResponse result = savingsGoalService.update(goalId, requestWith(null, null), USER_ID);

        assertThat(result.currentAmount()).isEqualByComparingTo("2500");
    }

    @Test
    void update_preserves_id_owner_and_archived_flag() {
        UUID goalId = UUID.randomUUID();
        SavingsGoal existing = goalBuilder().id(goalId).archived(true).build();
        given(savingsGoalRepository.findByIdAndUserId(goalId, USER_ID)).willReturn(Optional.of(existing));
        given(savingsGoalRepository.save(any(SavingsGoal.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        savingsGoalService.update(goalId, requestWith(new BigDecimal("3000"), null), USER_ID);

        then(savingsGoalRepository).should().save(argThat(g ->
                goalId.equals(g.getId()) && USER_ID.equals(g.getUserId()) && g.isArchived()));
    }

    // =========================================================================
    // archive()
    // =========================================================================

    @Test
    void archive_marks_the_goal_archived_and_keeps_all_other_fields() {
        UUID goalId = UUID.randomUUID();
        SavingsGoal existing = goalBuilder().id(goalId).icon("🎯").color("#3b82f6").build();
        given(savingsGoalRepository.findByIdAndUserId(goalId, USER_ID)).willReturn(Optional.of(existing));
        given(savingsGoalRepository.save(any(SavingsGoal.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        SavingsGoalResponse result = savingsGoalService.archive(goalId, USER_ID);

        assertThat(result.archived()).isTrue();
        assertThat(result.name()).isEqualTo("Emergency Fund");
        assertThat(result.icon()).isEqualTo("🎯");
        assertThat(result.targetAmount()).isEqualByComparingTo("10000");
    }

    @Test
    void archive_throws_ResourceNotFoundException_when_goal_belongs_to_another_user() {
        UUID goalId = UUID.randomUUID();
        given(savingsGoalRepository.findByIdAndUserId(goalId, "attacker")).willReturn(Optional.empty());

        assertThatThrownBy(() -> savingsGoalService.archive(goalId, "attacker"))
                .isInstanceOf(ResourceNotFoundException.class);
        then(savingsGoalRepository).should(never()).save(any());
    }

    // =========================================================================
    // delete()
    // =========================================================================

    @Test
    void delete_removes_the_goal_when_it_belongs_to_the_user() {
        UUID goalId = UUID.randomUUID();
        given(savingsGoalRepository.findByIdAndUserId(goalId, USER_ID))
                .willReturn(Optional.of(goalBuilder().id(goalId).build()));

        savingsGoalService.delete(goalId, USER_ID);

        then(savingsGoalRepository).should().deleteById(goalId);
    }

    @Test
    void delete_never_calls_deleteById_when_goal_is_not_found() {
        UUID goalId = UUID.randomUUID();
        given(savingsGoalRepository.findByIdAndUserId(goalId, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> savingsGoalService.delete(goalId, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        then(savingsGoalRepository).should(never()).deleteById(any());
    }

    // =========================================================================
    // Derived fields: progressPercentage
    // =========================================================================

    @Test
    void progress_is_zero_when_target_amount_is_zero() {
        given(savingsGoalRepository.findByUserIdAndArchivedFalseOrderByTargetDateAscNameAsc(USER_ID))
                .willReturn(List.of(goalBuilder()
                        .targetAmount(BigDecimal.ZERO)
                        .currentAmount(new BigDecimal("500"))
                        .build()));

        List<SavingsGoalResponse> result = savingsGoalService.getAll(USER_ID);

        assertThat(result.get(0).progressPercentage()).isZero();
    }

    @Test
    void progress_reflects_the_ratio_of_current_to_target() {
        given(savingsGoalRepository.findByUserIdAndArchivedFalseOrderByTargetDateAscNameAsc(USER_ID))
                .willReturn(List.of(goalBuilder()
                        .targetAmount(new BigDecimal("10000"))
                        .currentAmount(new BigDecimal("2500"))
                        .build()));

        List<SavingsGoalResponse> result = savingsGoalService.getAll(USER_ID);

        assertThat(result.get(0).progressPercentage()).isCloseTo(25.0, within(0.001));
    }

    @Test
    void progress_is_capped_at_100_when_goal_is_overachieved() {
        given(savingsGoalRepository.findByUserIdAndArchivedFalseOrderByTargetDateAscNameAsc(USER_ID))
                .willReturn(List.of(goalBuilder()
                        .targetAmount(new BigDecimal("1000"))
                        .currentAmount(new BigDecimal("1500"))
                        .build()));

        List<SavingsGoalResponse> result = savingsGoalService.getAll(USER_ID);

        assertThat(result.get(0).progressPercentage()).isEqualTo(100.0);
    }

    // =========================================================================
    // Derived fields: monthlyRequired
    // =========================================================================

    @Test
    void monthlyRequired_is_null_when_no_target_date_is_set() {
        given(savingsGoalRepository.findByUserIdAndArchivedFalseOrderByTargetDateAscNameAsc(USER_ID))
                .willReturn(List.of(goalBuilder().targetDate(null).build()));

        List<SavingsGoalResponse> result = savingsGoalService.getAll(USER_ID);

        assertThat(result.get(0).monthlyRequired()).isNull();
    }

    @Test
    void monthlyRequired_is_null_when_target_date_lies_in_the_past() {
        LocalDate pastDate = LocalDate.now(SwissTime.ZONE).minusMonths(2);
        given(savingsGoalRepository.findByUserIdAndArchivedFalseOrderByTargetDateAscNameAsc(USER_ID))
                .willReturn(List.of(goalBuilder().targetDate(pastDate).build()));

        List<SavingsGoalResponse> result = savingsGoalService.getAll(USER_ID);

        assertThat(result.get(0).monthlyRequired()).isNull();
    }

    @Test
    void monthlyRequired_is_null_when_target_date_is_within_the_current_month() {
        // Same month → 0 months between month starts → cannot compute a rate
        LocalDate endOfThisMonth = LocalDate.now(SwissTime.ZONE).withDayOfMonth(
                LocalDate.now(SwissTime.ZONE).lengthOfMonth());
        given(savingsGoalRepository.findByUserIdAndArchivedFalseOrderByTargetDateAscNameAsc(USER_ID))
                .willReturn(List.of(goalBuilder().targetDate(endOfThisMonth).build()));

        List<SavingsGoalResponse> result = savingsGoalService.getAll(USER_ID);

        assertThat(result.get(0).monthlyRequired()).isNull();
    }

    @Test
    void monthlyRequired_is_zero_when_goal_is_already_reached() {
        LocalDate futureDate = LocalDate.now(SwissTime.ZONE).plusMonths(6);
        given(savingsGoalRepository.findByUserIdAndArchivedFalseOrderByTargetDateAscNameAsc(USER_ID))
                .willReturn(List.of(goalBuilder()
                        .targetAmount(new BigDecimal("1000"))
                        .currentAmount(new BigDecimal("1000"))
                        .targetDate(futureDate)
                        .build()));

        List<SavingsGoalResponse> result = savingsGoalService.getAll(USER_ID);

        assertThat(result.get(0).monthlyRequired()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void monthlyRequired_divides_the_remaining_amount_over_the_remaining_months() {
        // 10 months between month starts regardless of the current day of month
        LocalDate targetDate = LocalDate.now(SwissTime.ZONE).plusMonths(10).withDayOfMonth(15);
        given(savingsGoalRepository.findByUserIdAndArchivedFalseOrderByTargetDateAscNameAsc(USER_ID))
                .willReturn(List.of(goalBuilder()
                        .targetAmount(new BigDecimal("10000"))
                        .currentAmount(new BigDecimal("2500"))
                        .targetDate(targetDate)
                        .build()));

        List<SavingsGoalResponse> result = savingsGoalService.getAll(USER_ID);

        assertThat(result.get(0).monthlyRequired()).isEqualByComparingTo("750.00");
    }
}
