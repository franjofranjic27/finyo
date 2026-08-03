package ch.finyo.profile;

import ch.finyo.common.SwissTime;
import ch.finyo.common.money.CurrencyCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    /** Reference retirement age used for the derived planning figures (AHV reference age). */
    static final int REFERENCE_RETIREMENT_AGE = 65;

    /** Plausibility floor for birth dates; anything earlier is a typo, not a person. */
    private static final int MIN_BIRTH_YEAR = 1900;

    private final UserProfileRepository userProfileRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse get(String userId) {
        log.debug("Fetching user profile for user={}", userId);
        LocalDate today = LocalDate.now(SwissTime.ZONE);
        return userProfileRepository.findByUserId(userId)
                .map(profile -> UserProfileResponse.from(profile, today))
                // default view is computed on the fly and never persisted on GET
                .orElseGet(() -> UserProfileResponse.from(UserProfile.withDefaults(userId), today));
    }

    /**
     * Full replace of the master data — every field of the request is written,
     * null clearing the stored value. Never use this for a single preference
     * toggle; that is what {@link #updatePreferences} is for.
     */
    @Transactional
    public UserProfileResponse upsert(UserProfileRequest request, String userId) {
        log.info("Upserting user profile for user={}", userId);
        validateBirthDate(request.birthDate());
        var existing = userProfileRepository.findByUserId(userId);
        UUID existingId = existing.map(UserProfile::getId).orElse(null);
        boolean onboardingCompleted = request.onboardingCompleted() != null
                ? request.onboardingCompleted()
                : existing.map(UserProfile::isOnboardingCompleted).orElse(false);

        var profile = UserProfile.builder()
                .id(existingId)
                .userId(userId)
                .salutation(request.salutation())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .birthDate(request.birthDate())
                .civilStatus(request.civilStatus())
                .churchAffiliation(request.churchAffiliation())
                .nationality(request.nationality())
                .street(request.street())
                .postalCode(request.postalCode())
                .city(request.city())
                .municipality(request.municipality())
                .cantonCode(request.cantonCode())
                .phone(request.phone())
                .preferredLanguage(request.preferredLanguage())
                .theme(request.theme() != null ? request.theme() : Theme.SYSTEM)
                .defaultCurrency(defaultCurrencyOrChf(request.defaultCurrency()))
                .onboardingCompleted(onboardingCompleted)
                .build();

        UserProfile saved = userProfileRepository.save(profile);
        log.info("Upserted user profile id={} for user={}", saved.getId(), userId);
        return UserProfileResponse.from(saved, LocalDate.now(SwissTime.ZONE));
    }

    /**
     * Applies only the non-null preference fields; the master data and the
     * onboarding flag of the stored row are preserved. Users may toggle the
     * theme before ever saving a profile, so a missing row is created with
     * the defaults instead of failing.
     */
    @Transactional
    public UserProfileResponse updatePreferences(PreferencesPatchRequest patch, String userId) {
        if (patch.isEmpty()) {
            throw new IllegalArgumentException("at least one preference must be provided");
        }
        log.info("Updating profile preferences for user={}", userId);

        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseGet(() -> UserProfile.withDefaults(userId));

        var builder = profile.toBuilder();
        if (patch.theme() != null) {
            builder.theme(patch.theme());
        }
        if (patch.preferredLanguage() != null) {
            builder.preferredLanguage(patch.preferredLanguage());
        }
        if (patch.defaultCurrency() != null) {
            builder.defaultCurrency(new CurrencyCode(patch.defaultCurrency()));
        }

        UserProfile saved = userProfileRepository.save(builder.build());
        log.info("Updated profile preferences id={} for user={}", saved.getId(), userId);
        return UserProfileResponse.from(saved, LocalDate.now(SwissTime.ZONE));
    }

    /** Mirrors the theme handling: an absent preference falls back to the column default. */
    private static CurrencyCode defaultCurrencyOrChf(String defaultCurrency) {
        return defaultCurrency != null ? new CurrencyCode(defaultCurrency) : CurrencyCode.CHF;
    }

    private static void validateBirthDate(LocalDate birthDate) {
        if (birthDate != null && birthDate.getYear() < MIN_BIRTH_YEAR) {
            throw new IllegalArgumentException("birthDate must be in 1900 or later");
        }
    }

    /** Age and retirement figures derived from the birth date; all null without one. */
    record Derived(Integer age, Integer yearsToRetirement, Integer retirementYear) {

        static final Derived EMPTY = new Derived(null, null, null);
    }

    static Derived derive(LocalDate birthDate, LocalDate today) {
        if (birthDate == null) {
            return Derived.EMPTY;
        }
        int age = Period.between(birthDate, today).getYears();
        int yearsToRetirement = Math.max(0, REFERENCE_RETIREMENT_AGE - age);
        int retirementYear = birthDate.getYear() + REFERENCE_RETIREMENT_AGE;
        return new Derived(age, yearsToRetirement, retirementYear);
    }
}
