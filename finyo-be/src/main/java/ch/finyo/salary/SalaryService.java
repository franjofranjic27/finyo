package ch.finyo.salary;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalaryService {

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
        UUID existingId = salaryProfileRepository.findByUserId(userId)
                .map(SalaryProfile::getId)
                .orElse(null);

        var profile = SalaryProfile.builder()
                .id(existingId)
                .userId(userId)
                .grossMonthly(request.grossMonthly())
                .thirteenthSalary(Boolean.TRUE.equals(request.thirteenthSalary()))
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
}
