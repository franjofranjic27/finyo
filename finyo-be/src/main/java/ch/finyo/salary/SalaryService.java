package ch.finyo.salary;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalaryService {

    private static final BigDecimal TWELVE = new BigDecimal("12");
    private static final BigDecimal THIRTEEN = new BigDecimal("13");

    private final SalaryProfileRepository salaryProfileRepository;

    @Transactional(readOnly = true)
    public SalaryResponse get(String userId) {
        log.debug("Fetching salary profile for user={}", userId);
        return salaryProfileRepository.findByUserId(userId)
                .map(SalaryResponse::from)
                // default view is computed on the fly and never persisted on GET
                .orElseGet(() -> SalaryResponse.from(SalaryProfile.withDefaults(userId)));
    }

    @Transactional
    public SalaryResponse upsert(SalaryRequest request, String userId) {
        log.info("Upserting salary profile for user={}", userId);
        SalaryInputMode inputMode = request.inputMode() != null ? request.inputMode() : SalaryInputMode.MONTHLY;
        validateGrossForMode(inputMode, request);
        UUID existingId = salaryProfileRepository.findByUserId(userId)
                .map(SalaryProfile::getId)
                .orElse(null);

        boolean thirteenthSalary = Boolean.TRUE.equals(request.thirteenthSalary());
        BigDecimal monthsPerYear = thirteenthSalary ? THIRTEEN : TWELVE;
        // the entered gross is authoritative; the counterpart is derived
        BigDecimal grossYearly = inputMode == SalaryInputMode.YEARLY
                ? request.grossYearly()
                : request.grossMonthly().multiply(monthsPerYear);
        BigDecimal grossMonthly = inputMode == SalaryInputMode.YEARLY
                ? request.grossYearly().divide(monthsPerYear, 2, RoundingMode.HALF_UP)
                : request.grossMonthly();

        var profile = SalaryProfile.builder()
                .id(existingId)
                .userId(userId)
                .inputMode(inputMode)
                .grossMonthly(grossMonthly)
                .grossYearly(grossYearly)
                .thirteenthSalary(thirteenthSalary)
                .ahvPct(request.ahvPct())
                .alvPct(request.alvPct())
                .nbuPct(request.nbuPct())
                .ktgPct(request.ktgPct())
                .pensionFixed(request.pensionFixed())
                .otherFixed(request.otherFixed())
                .build();

        SalaryProfile saved = salaryProfileRepository.save(profile);
        log.info("Upserted salary profile id={} for user={}", saved.getId(), userId);
        return SalaryResponse.from(saved);
    }

    private static void validateGrossForMode(SalaryInputMode inputMode, SalaryRequest request) {
        if (inputMode == SalaryInputMode.YEARLY) {
            if (request.grossYearly() == null) {
                throw new IllegalArgumentException("grossYearly is required for YEARLY input mode");
            }
        } else if (request.grossMonthly() == null) {
            throw new IllegalArgumentException("grossMonthly is required for MONTHLY input mode");
        }
    }
}
