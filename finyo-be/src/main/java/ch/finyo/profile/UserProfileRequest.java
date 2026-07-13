package ch.finyo.profile;

import ch.finyo.tax.ChurchAffiliation;
import ch.finyo.tax.TaxCivilStatus;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

/**
 * PUT payload for the user profile (full replace of the master data).
 *
 * <p>Semantics: every master-data field (birthDate, civilStatus,
 * churchAffiliation, preferredLanguage) is replaced with the request value,
 * null clearing the stored value. A null {@code theme} falls back to SYSTEM.
 * A null {@code onboardingCompleted} preserves the stored value, so profile
 * edits after onboarding never reset the flag.
 */
public record UserProfileRequest(
        @Past LocalDate birthDate,
        TaxCivilStatus civilStatus,
        ChurchAffiliation churchAffiliation,
        @Pattern(regexp = "de|en") String preferredLanguage,
        Theme theme,
        Boolean onboardingCompleted
) {}
