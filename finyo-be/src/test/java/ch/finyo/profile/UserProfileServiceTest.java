package ch.finyo.profile;

import ch.finyo.tax.ChurchAffiliation;
import ch.finyo.tax.TaxCivilStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

/**
 * Pure unit tests for UserProfileService.
 *
 * Focus areas:
 *   1. GET without a row: defaults (theme SYSTEM, onboarding false), nothing persisted.
 *   2. Upsert semantics: first PUT creates the row, later PUTs reuse its id;
 *      a null onboardingCompleted preserves the stored flag.
 *   3. updatePreferences(): partial patch — only the given preference is
 *      applied, the master data survives; empty patches are rejected.
 *   4. derive(): age, yearsToRetirement and retirementYear including the
 *      birthday-today, past-65 and missing-birth-date edge cases.
 */
@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    private static final String USER_ID = "user-profile-1";

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private UserProfileService userProfileService;

    private UserProfile referenceProfile(UUID id, boolean onboardingCompleted) {
        return UserProfile.builder()
                .id(id)
                .userId(USER_ID)
                .birthDate(LocalDate.of(1990, 4, 15))
                .civilStatus(TaxCivilStatus.MARRIED)
                .churchAffiliation(ChurchAffiliation.NONE)
                .preferredLanguage("de")
                .theme(Theme.DARK)
                .onboardingCompleted(onboardingCompleted)
                .build();
    }

    private UserProfileRequest referenceRequest(Boolean onboardingCompleted) {
        return new UserProfileRequest(
                LocalDate.of(1990, 4, 15),
                TaxCivilStatus.MARRIED,
                ChurchAffiliation.NONE,
                "de",
                Theme.DARK,
                onboardingCompleted);
    }

    // =========================================================================
    // get()
    // =========================================================================

    @Test
    void get_returns_defaults_without_persisting_when_no_row_exists() {
        given(userProfileRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        UserProfileResponse response = userProfileService.get(USER_ID);

        assertThat(response.birthDate()).isNull();
        assertThat(response.civilStatus()).isNull();
        assertThat(response.churchAffiliation()).isNull();
        assertThat(response.preferredLanguage()).isNull();
        assertThat(response.theme()).isEqualTo(Theme.SYSTEM);
        assertThat(response.onboardingCompleted()).isFalse();
        assertThat(response.age()).isNull();
        assertThat(response.yearsToRetirement()).isNull();
        assertThat(response.retirementYear()).isNull();

        then(userProfileRepository).should(never()).save(any());
    }

    @Test
    void get_returns_the_stored_profile_with_derived_figures() {
        given(userProfileRepository.findByUserId(USER_ID))
                .willReturn(Optional.of(referenceProfile(UUID.randomUUID(), true)));

        UserProfileResponse response = userProfileService.get(USER_ID);

        assertThat(response.birthDate()).isEqualTo(LocalDate.of(1990, 4, 15));
        assertThat(response.civilStatus()).isEqualTo(TaxCivilStatus.MARRIED);
        assertThat(response.theme()).isEqualTo(Theme.DARK);
        assertThat(response.onboardingCompleted()).isTrue();
        assertThat(response.age()).isNotNull();
        assertThat(response.retirementYear()).isEqualTo(2055);
    }

    // =========================================================================
    // upsert()
    // =========================================================================

    @Test
    void upsert_creates_a_new_row_on_first_put() {
        given(userProfileRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(userProfileRepository.save(any(UserProfile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse response = userProfileService.upsert(referenceRequest(true), USER_ID);

        assertThat(response.onboardingCompleted()).isTrue();
        assertThat(response.retirementYear()).isEqualTo(2055);
        then(userProfileRepository).should()
                .save(argThat(p -> p.getId() == null && USER_ID.equals(p.getUserId())));
    }

    @Test
    void upsert_reuses_the_existing_row_id_on_subsequent_puts() {
        UUID existingId = UUID.randomUUID();
        given(userProfileRepository.findByUserId(USER_ID))
                .willReturn(Optional.of(referenceProfile(existingId, false)));
        given(userProfileRepository.save(any(UserProfile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse response = userProfileService.upsert(referenceRequest(true), USER_ID);

        assertThat(response.civilStatus()).isEqualTo(TaxCivilStatus.MARRIED);
        then(userProfileRepository).should()
                .save(argThat(p -> existingId.equals(p.getId()) && USER_ID.equals(p.getUserId())));
    }

    @Test
    void upsert_with_null_onboarding_completed_preserves_the_stored_true_flag() {
        given(userProfileRepository.findByUserId(USER_ID))
                .willReturn(Optional.of(referenceProfile(UUID.randomUUID(), true)));
        given(userProfileRepository.save(any(UserProfile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse response = userProfileService.upsert(referenceRequest(null), USER_ID);

        assertThat(response.onboardingCompleted()).isTrue();
        then(userProfileRepository).should().save(argThat(UserProfile::isOnboardingCompleted));
    }

    @Test
    void upsert_with_a_birth_date_before_1900_is_rejected() {
        UserProfileRequest request = new UserProfileRequest(
                LocalDate.of(1899, 12, 31), null, null, null, null, null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> userProfileService.upsert(request, USER_ID))
                .withMessage("birthDate must be in 1900 or later");

        then(userProfileRepository).should(never()).save(any());
    }

    @Test
    void upsert_with_null_theme_falls_back_to_system() {
        given(userProfileRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(userProfileRepository.save(any(UserProfile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        UserProfileRequest request = new UserProfileRequest(null, null, null, null, null, null);
        UserProfileResponse response = userProfileService.upsert(request, USER_ID);

        assertThat(response.theme()).isEqualTo(Theme.SYSTEM);
        assertThat(response.onboardingCompleted()).isFalse();
    }

    // =========================================================================
    // updatePreferences()
    // =========================================================================

    @Test
    void update_preferences_applies_only_the_theme_and_preserves_the_master_data() {
        UUID existingId = UUID.randomUUID();
        given(userProfileRepository.findByUserId(USER_ID))
                .willReturn(Optional.of(referenceProfile(existingId, true)));
        given(userProfileRepository.save(any(UserProfile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse response = userProfileService.updatePreferences(
                new PreferencesPatchRequest(Theme.LIGHT, null), USER_ID);

        assertThat(response.theme()).isEqualTo(Theme.LIGHT);
        assertThat(response.birthDate()).isEqualTo(LocalDate.of(1990, 4, 15));
        assertThat(response.civilStatus()).isEqualTo(TaxCivilStatus.MARRIED);
        assertThat(response.churchAffiliation()).isEqualTo(ChurchAffiliation.NONE);
        assertThat(response.preferredLanguage()).isEqualTo("de");
        assertThat(response.onboardingCompleted()).isTrue();
        then(userProfileRepository).should().save(argThat(p -> existingId.equals(p.getId())));
    }

    @Test
    void update_preferences_applies_only_the_language_and_preserves_the_theme() {
        given(userProfileRepository.findByUserId(USER_ID))
                .willReturn(Optional.of(referenceProfile(UUID.randomUUID(), true)));
        given(userProfileRepository.save(any(UserProfile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse response = userProfileService.updatePreferences(
                new PreferencesPatchRequest(null, "en"), USER_ID);

        assertThat(response.preferredLanguage()).isEqualTo("en");
        assertThat(response.theme()).isEqualTo(Theme.DARK);
        assertThat(response.birthDate()).isEqualTo(LocalDate.of(1990, 4, 15));
        assertThat(response.civilStatus()).isEqualTo(TaxCivilStatus.MARRIED);
    }

    @Test
    void update_preferences_creates_a_default_profile_when_no_row_exists() {
        given(userProfileRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(userProfileRepository.save(any(UserProfile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse response = userProfileService.updatePreferences(
                new PreferencesPatchRequest(Theme.DARK, null), USER_ID);

        assertThat(response.theme()).isEqualTo(Theme.DARK);
        assertThat(response.birthDate()).isNull();
        assertThat(response.preferredLanguage()).isNull();
        assertThat(response.onboardingCompleted()).isFalse();
        then(userProfileRepository).should()
                .save(argThat(p -> p.getId() == null && USER_ID.equals(p.getUserId())));
    }

    @Test
    void update_preferences_with_an_all_null_patch_is_rejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> userProfileService.updatePreferences(
                        new PreferencesPatchRequest(null, null), USER_ID))
                .withMessage("at least one preference must be provided");

        then(userProfileRepository).should(never()).save(any());
        then(userProfileRepository).should(never()).findByUserId(any());
    }

    // =========================================================================
    // derive()
    // =========================================================================

    @Test
    void derive_computes_age_and_retirement_figures() {
        var derived = UserProfileService.derive(
                LocalDate.of(1990, 4, 15), LocalDate.of(2026, 7, 13));

        assertThat(derived.age()).isEqualTo(36);
        assertThat(derived.yearsToRetirement()).isEqualTo(29);
        assertThat(derived.retirementYear()).isEqualTo(2055);
    }

    @Test
    void derive_counts_the_birthday_itself_as_the_new_age() {
        var derived = UserProfileService.derive(
                LocalDate.of(1990, 7, 13), LocalDate.of(2026, 7, 13));

        assertThat(derived.age()).isEqualTo(36);
        assertThat(derived.yearsToRetirement()).isEqualTo(29);
    }

    @Test
    void derive_clamps_years_to_retirement_at_zero_past_the_reference_age() {
        var derived = UserProfileService.derive(
                LocalDate.of(1950, 1, 1), LocalDate.of(2026, 7, 13));

        assertThat(derived.age()).isEqualTo(76);
        assertThat(derived.yearsToRetirement()).isZero();
        assertThat(derived.retirementYear()).isEqualTo(2015);
    }

    @Test
    void derive_returns_all_nulls_without_a_birth_date() {
        var derived = UserProfileService.derive(null, LocalDate.of(2026, 7, 13));

        assertThat(derived.age()).isNull();
        assertThat(derived.yearsToRetirement()).isNull();
        assertThat(derived.retirementYear()).isNull();
    }
}
