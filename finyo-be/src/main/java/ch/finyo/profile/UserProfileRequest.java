package ch.finyo.profile;

import ch.finyo.tax.ChurchAffiliation;
import ch.finyo.tax.TaxCivilStatus;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * PUT payload for the user profile (full replace of the master data).
 *
 * <p>Semantics: every master-data field (person, address, contact, language)
 * is replaced with the request value, null clearing the stored value. A null
 * {@code theme} falls back to SYSTEM and a null {@code defaultCurrency} to
 * CHF. A null {@code onboardingCompleted} preserves the stored value, so
 * profile edits after onboarding never reset the flag.
 *
 * <p>Callers must therefore always send the complete field set. For partial
 * preference updates use {@link PreferencesPatchRequest} via
 * {@code PATCH /api/v1/profile/preferences}.
 *
 * <p>Postal code, phone and nationality are deliberately free text (size
 * limits only) — international users are possible.
 */
public record UserProfileRequest(
        Salutation salutation,
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Past LocalDate birthDate,
        TaxCivilStatus civilStatus,
        ChurchAffiliation churchAffiliation,
        @Size(max = 100) String nationality,
        @Size(max = 200) String street,
        @Size(max = 10) String postalCode,
        @Size(max = 100) String city,
        @Size(max = 100) String municipality,
        @Pattern(regexp = "[A-Z]{2}") String cantonCode,
        @Size(max = 30) String phone,
        @Pattern(regexp = "de|en") String preferredLanguage,
        Theme theme,
        @Pattern(regexp = "[A-Z]{3}") String defaultCurrency,
        Boolean onboardingCompleted
) {}
