package ch.finyo.profile;

import ch.finyo.tax.ChurchAffiliation;
import ch.finyo.tax.TaxCivilStatus;

import java.time.LocalDate;

/**
 * Stored (or default) user profile plus figures derived from the birth date;
 * age, yearsToRetirement and retirementYear are null without a birth date.
 */
public record UserProfileResponse(
        LocalDate birthDate,
        TaxCivilStatus civilStatus,
        ChurchAffiliation churchAffiliation,
        String preferredLanguage,
        Theme theme,
        boolean onboardingCompleted,
        Integer age,
        Integer yearsToRetirement,
        Integer retirementYear
) {
    public static UserProfileResponse from(UserProfile profile, LocalDate today) {
        var derived = UserProfileService.derive(profile.getBirthDate(), today);
        return new UserProfileResponse(
                profile.getBirthDate(),
                profile.getCivilStatus(),
                profile.getChurchAffiliation(),
                profile.getPreferredLanguage(),
                profile.getTheme(),
                profile.isOnboardingCompleted(),
                derived.age(),
                derived.yearsToRetirement(),
                derived.retirementYear());
    }
}
