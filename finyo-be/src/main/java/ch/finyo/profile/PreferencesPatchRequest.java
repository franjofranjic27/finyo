package ch.finyo.profile;

import jakarta.validation.constraints.Pattern;

/**
 * Partial update of the UI preferences: only the non-null fields are applied,
 * the master data (person, address, contact) and the onboarding flag are left
 * untouched.
 *
 * <p>This is the endpoint for single-preference toggles (theme switch,
 * language switch, default currency). Sending them through the full-replace
 * {@link UserProfileRequest} would clear every master-data field that the
 * caller does not resend. An all-null patch is rejected in
 * {@link UserProfileService#updatePreferences}.
 */
public record PreferencesPatchRequest(
        Theme theme,
        @Pattern(regexp = "de|en") String preferredLanguage,
        @Pattern(regexp = "[A-Z]{3}") String defaultCurrency
) {

    boolean isEmpty() {
        return theme == null && preferredLanguage == null && defaultCurrency == null;
    }
}
