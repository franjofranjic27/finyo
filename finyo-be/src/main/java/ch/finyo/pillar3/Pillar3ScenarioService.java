package ch.finyo.pillar3;

import ch.finyo.common.ResourceNotFoundException;
import ch.finyo.tax.Pillar3CalculationService;
import ch.finyo.tax.Pillar3InputRequest;
import ch.finyo.tax.Pillar3ResultResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class Pillar3ScenarioService {

    private final Pillar3ScenarioRepository scenarioRepository;
    private final Pillar3ProductRepository productRepository;
    private final Pillar3CalculationService pillar3CalculationService;

    @Transactional(readOnly = true)
    public List<Pillar3ScenarioResponse> list(String userId) {
        log.debug("Fetching pillar3 scenarios for user={}", userId);
        return scenarioRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public Pillar3ScenarioResponse create(Pillar3ScenarioRequest request, String userId) {
        log.info("Creating pillar3 scenario '{}' for user={}", request.name(), userId);
        if (request.productId() != null && !productRepository.existsById(request.productId())) {
            throw ResourceNotFoundException.of("Pillar3Product", request.productId());
        }

        if (request.isDefault() && scenarioRepository.existsByUserIdAndIsDefaultTrue(userId)) {
            throw new IllegalStateException("User already has a default pillar 3a scenario");
        }

        try {
            // Flush eagerly: the pre-check above is racy, so a concurrent default
            // insert must surface here (partial unique index
            // ux_pillar3_scenario_default_per_user) instead of as a 500 at commit.
            Pillar3Scenario saved = scenarioRepository.saveAndFlush(Pillar3Scenario.builder()
                    .userId(userId)
                    .name(request.name())
                    .isDefault(request.isDefault())
                    .currentBalance(request.currentBalance())
                    .annualContribution(request.annualContribution())
                    .assumedAnnualReturnPercent(BigDecimal.valueOf(request.assumedAnnualReturnPercent()))
                    .yearsToRetirement(request.yearsToRetirement())
                    .grossEmploymentIncome(request.grossEmploymentIncome())
                    .civilStatus(request.civilStatus())
                    .cantonCode(request.cantonCode())
                    .taxYear(request.taxYear())
                    .productId(request.productId())
                    .build());
            return toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("User already has a default pillar 3a scenario", e);
        }
    }

    @Transactional
    public Pillar3ScenarioResponse setDefault(UUID scenarioId, String userId) {
        log.info("Setting default pillar3 scenario id={} for user={}", scenarioId, userId);
        Pillar3Scenario target = loadScenario(scenarioId, userId);
        if (target.isDefault()) {
            return toResponse(target);
        }

        // Flush the cleared flag before setting the new one, otherwise the
        // partial unique index ux_pillar3_scenario_default_per_user is violated.
        scenarioRepository.findByUserIdAndIsDefaultTrue(userId)
                .ifPresent(current -> scenarioRepository
                        .saveAndFlush(current.toBuilder().isDefault(false).build()));

        Pillar3Scenario saved = scenarioRepository.save(target.toBuilder().isDefault(true).build());
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID scenarioId, String userId) {
        log.info("Deleting pillar3 scenario id={} for user={}", scenarioId, userId);
        scenarioRepository.delete(loadScenario(scenarioId, userId));
    }

    private Pillar3Scenario loadScenario(UUID scenarioId, String userId) {
        return scenarioRepository.findByIdAndUserId(scenarioId, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Pillar3 scenario", scenarioId));
    }

    private Pillar3ScenarioResponse toResponse(Pillar3Scenario scenario) {
        // May be empty even for a linked scenario: the product FK is ON DELETE
        // SET NULL, so a deleted product degrades to the stored snapshot percent.
        Optional<Pillar3Product> product = Optional.ofNullable(scenario.getProductId())
                .flatMap(productRepository::findById);

        double effectiveReturnPercent = product
                .map(p -> Pillar3ReturnModel.netReturnPct(p.getEquityPct(), p.getTerPct()).doubleValue())
                .orElseGet(() -> scenario.getAssumedAnnualReturnPercent().doubleValue());

        return new Pillar3ScenarioResponse(
                scenario.getId(),
                scenario.getName(),
                scenario.isDefault(),
                Pillar3ScenarioInputs.from(scenario),
                product.map(this::toProductResponse).orElse(null),
                effectiveReturnPercent,
                recompute(scenario, effectiveReturnPercent),
                scenario.getCreatedAt()
        );
    }

    private Pillar3ResultResponse recompute(Pillar3Scenario scenario, double effectiveReturnPercent) {
        return pillar3CalculationService.calculate(new Pillar3InputRequest(
                scenario.getCurrentBalance(),
                scenario.getAnnualContribution(),
                effectiveReturnPercent,
                scenario.getYearsToRetirement(),
                scenario.getGrossEmploymentIncome(),
                scenario.getCivilStatus(),
                scenario.getCantonCode(),
                // 0 means "current year" in Pillar3CalculationService
                scenario.getTaxYear() != null ? scenario.getTaxYear() : 0
        ));
    }

    private Pillar3ProductResponse toProductResponse(Pillar3Product product) {
        return new Pillar3ProductResponse(
                product.getId(),
                product.getProvider(),
                product.getName(),
                product.getIsin(),
                product.getValor(),
                product.getEquityPct(),
                product.getTerPct(),
                product.isActive(),
                product.getSortOrder(),
                Pillar3ReturnModel.grossReturnPct(product.getEquityPct()),
                Pillar3ReturnModel.netReturnPct(product.getEquityPct(), product.getTerPct())
        );
    }
}
