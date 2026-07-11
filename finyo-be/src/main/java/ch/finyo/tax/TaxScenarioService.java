package ch.finyo.tax;

import ch.finyo.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaxScenarioService {

    private final TaxScenarioRepository taxScenarioRepository;
    private final TaxYearRepository taxYearRepository;
    private final TaxCalculationService taxCalculationService;

    public List<TaxScenarioResponse> list(int year, String userId) {
        log.debug("Fetching tax scenarios for year={} user={}", year, userId);
        return taxYearRepository.findByUserIdAndYear(userId, year)
                .map(taxYear -> taxScenarioRepository
                        .findByTaxYearIdAndUserIdOrderByCreatedAtDesc(taxYear.getId(), userId)
                        .stream()
                        .map(scenario -> toResponse(scenario, year))
                        .toList())
                .orElseGet(List::of);
    }

    @Transactional
    public TaxScenarioResponse create(int year, TaxScenarioRequest request, String userId) {
        log.info("Creating tax scenario '{}' for year={} user={}", request.name(), year, userId);
        TaxYear taxYear = taxYearRepository.findByUserIdAndYear(userId, year)
                .orElseGet(() -> createMinimalYear(year, userId));

        if (request.isDefault()
                && taxScenarioRepository.existsByTaxYearIdAndUserIdAndIsDefaultTrue(taxYear.getId(), userId)) {
            throw new IllegalStateException("Tax year %d already has a default scenario".formatted(year));
        }

        try {
            // Flush eagerly: the pre-check above is racy, so a concurrent default
            // insert must surface here (partial unique index
            // ux_tax_scenario_default_per_year) instead of as a 500 at commit.
            TaxScenario saved = taxScenarioRepository.saveAndFlush(TaxScenario.builder()
                    .userId(userId)
                    .taxYearId(taxYear.getId())
                    .name(request.name())
                    .isDefault(request.isDefault())
                    .cantonCode(request.cantonCode())
                    .bfsNumber(request.bfsNumber())
                    .civilStatus(request.civilStatus())
                    .numberOfChildren(request.numberOfChildren())
                    .churchAffiliation(request.churchAffiliation())
                    .grossEmploymentIncome(request.grossEmploymentIncome())
                    .selfEmploymentIncome(request.selfEmploymentIncome())
                    .investmentIncome(request.investmentIncome())
                    .rentalIncome(request.rentalIncome())
                    .deductionProfessionalExpenses(request.deductionProfessionalExpenses())
                    .deductionInsurancePremiums(request.deductionInsurancePremiums())
                    .deductionCharitableDonations(request.deductionCharitableDonations())
                    .deductionDebtInterest(request.deductionDebtInterest())
                    .pillar3aContribution(request.pillar3aContribution())
                    .netWealth(request.netWealth())
                    .build());
            return toResponse(saved, year);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException(
                    "Tax year %d already has a default scenario".formatted(year), e);
        }
    }

    @Transactional
    public TaxScenarioResponse setDefault(int year, UUID scenarioId, String userId) {
        log.info("Setting default scenario id={} for year={} user={}", scenarioId, year, userId);
        TaxYear taxYear = loadYear(year, userId);
        TaxScenario target = loadScenario(taxYear, scenarioId, userId);
        if (target.isDefault()) {
            return toResponse(target, year);
        }

        // Flush the cleared flag before setting the new one, otherwise the
        // partial unique index ux_tax_scenario_default_per_year is violated.
        taxScenarioRepository.findByTaxYearIdAndUserIdAndIsDefaultTrue(taxYear.getId(), userId)
                .ifPresent(current -> taxScenarioRepository
                        .saveAndFlush(current.toBuilder().isDefault(false).build()));

        TaxScenario saved = taxScenarioRepository.save(target.toBuilder().isDefault(true).build());
        return toResponse(saved, year);
    }

    @Transactional
    public void delete(int year, UUID scenarioId, String userId) {
        log.info("Deleting tax scenario id={} for year={} user={}", scenarioId, year, userId);
        TaxYear taxYear = loadYear(year, userId);
        taxScenarioRepository.delete(loadScenario(taxYear, scenarioId, userId));
    }

    /** Scenarios may be saved before the year itself was ever filled in. */
    private TaxYear createMinimalYear(int year, String userId) {
        return taxYearRepository.save(TaxYear.builder()
                .userId(userId)
                .year(year)
                .status(TaxYearStatus.OPEN)
                .build());
    }

    private TaxYear loadYear(int year, String userId) {
        return taxYearRepository.findByUserIdAndYear(userId, year)
                .orElseThrow(() -> ResourceNotFoundException.of("Tax year", year));
    }

    private TaxScenario loadScenario(TaxYear taxYear, UUID scenarioId, String userId) {
        return taxScenarioRepository.findByIdAndUserId(scenarioId, userId)
                .filter(scenario -> scenario.getTaxYearId().equals(taxYear.getId()))
                .orElseThrow(() -> ResourceNotFoundException.of("Tax scenario", scenarioId));
    }

    private TaxScenarioResponse toResponse(TaxScenario scenario, int year) {
        return new TaxScenarioResponse(
                scenario.getId(),
                year,
                scenario.getName(),
                scenario.isDefault(),
                TaxYearInputs.from(scenario),
                recompute(scenario, year),
                scenario.getCreatedAt()
        );
    }

    private TaxResultResponse recompute(TaxScenario scenario, int year) {
        if (scenario.getCantonCode() == null
                || scenario.getCivilStatus() == null
                || scenario.getGrossEmploymentIncome() == null) {
            return null;
        }
        return taxCalculationService.calculate(new TaxInputRequest(
                year,
                scenario.getCantonCode(),
                scenario.getBfsNumber(),
                scenario.getCivilStatus(),
                scenario.getNumberOfChildren() != null ? scenario.getNumberOfChildren() : 0,
                scenario.getChurchAffiliation(),
                scenario.getGrossEmploymentIncome(),
                scenario.getSelfEmploymentIncome(),
                scenario.getInvestmentIncome(),
                scenario.getRentalIncome(),
                scenario.getDeductionProfessionalExpenses(),
                scenario.getDeductionInsurancePremiums(),
                scenario.getDeductionCharitableDonations(),
                scenario.getDeductionDebtInterest(),
                scenario.getPillar3aContribution(),
                scenario.getNetWealth()
        ));
    }
}
