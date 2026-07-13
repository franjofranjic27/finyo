package ch.finyo.profile;

import ch.finyo.common.SwissTime;
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

    @Transactional
    public UserProfileResponse upsert(UserProfileRequest request, String userId) {
        log.info("Upserting user profile for user={}", userId);
        var existing = userProfileRepository.findByUserId(userId);
        UUID existingId = existing.map(UserProfile::getId).orElse(null);
        boolean onboardingCompleted = request.onboardingCompleted() != null
                ? request.onboardingCompleted()
                : existing.map(UserProfile::isOnboardingCompleted).orElse(false);

        var profile = UserProfile.builder()
                .id(existingId)
                .userId(userId)
                .birthDate(request.birthDate())
                .civilStatus(request.civilStatus())
                .churchAffiliation(request.churchAffiliation())
                .preferredLanguage(request.preferredLanguage())
                .theme(request.theme() != null ? request.theme() : Theme.SYSTEM)
                .onboardingCompleted(onboardingCompleted)
                .build();

        UserProfile saved = userProfileRepository.save(profile);
        log.info("Upserted user profile id={} for user={}", saved.getId(), userId);
        return UserProfileResponse.from(saved, LocalDate.now(SwissTime.ZONE));
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
