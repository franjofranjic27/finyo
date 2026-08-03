package ch.finyo.profile;

import ch.finyo.common.money.CurrencyCode;
import ch.finyo.tax.ChurchAffiliation;
import ch.finyo.tax.TaxCivilStatus;

import java.time.LocalDate;

/**
 * Stored (or default) user profile plus figures derived from the birth date;
 * age, yearsToRetirement and retirementYear are null without a birth date.
 * The default currency is exposed as its plain three-letter code.
 */
public record UserProfileResponse(
        Salutation salutation,
        String firstName,
        String lastName,
        LocalDate birthDate,
        TaxCivilStatus civilStatus,
        ChurchAffiliation churchAffiliation,
        String nationality,
        String street,
        String postalCode,
        String city,
        String municipality,
        String cantonCode,
        String phone,
        String preferredLanguage,
        Theme theme,
        String defaultCurrency,
        boolean onboardingCompleted,
        Integer age,
        Integer yearsToRetirement,
        Integer retirementYear
) {
    public static UserProfileResponse from(UserProfile profile, LocalDate today) {
        var derived = UserProfileService.derive(profile.getBirthDate(), today);
        return new UserProfileResponse(
                profile.getSalutation(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getBirthDate(),
                profile.getCivilStatus(),
                profile.getChurchAffiliation(),
                profile.getNationality(),
                profile.getStreet(),
                profile.getPostalCode(),
                profile.getCity(),
                profile.getMunicipality(),
                profile.getCantonCode(),
                profile.getPhone(),
                profile.getPreferredLanguage(),
                profile.getTheme(),
                asCode(profile.getDefaultCurrency()),
                profile.isOnboardingCompleted(),
                derived.age(),
                derived.yearsToRetirement(),
                derived.retirementYear());
    }

    private static String asCode(CurrencyCode currency) {
        return currency == null ? null : currency.value();
    }
}
