package ch.finyo.pillar3;

import ch.finyo.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Projects the final capital of pillar 3a products over a given horizon using
 * the modelled return from {@link Pillar3ReturnModel}
 * ({@code grossRate = 0.01 + equityPct/100 × 0.05}).
 *
 * <p>Yearly loop for {@code i = 1..years}:
 * <pre>
 *   balance   = balance × (1 + grossRate)
 *   fee       = balance × terPct/100     // TER charged annually on the grown balance
 *   balance   = balance − fee + annualContribution
 *   totalFees += fee
 * </pre>
 * {@code finalCapital = balance}, {@code totalPaidIn = currentBalance + annualContribution × years}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Pillar3CompareService {

    /** 2025 legal maximum for employees with a 2nd pillar — exposed so the frontend never hardcodes it. */
    private static final BigDecimal MAX_ANNUAL_CONTRIBUTION = new BigDecimal("7258");

    private final Pillar3ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Pillar3CompareResponse compare(Pillar3CompareRequest request) {
        log.debug("Comparing {} pillar3 products over {} years", request.productIds().size(), request.years());

        List<Pillar3Product> products = resolveProducts(request.productIds());

        BigDecimal totalPaidIn = request.currentBalance()
                .add(request.annualContribution().multiply(BigDecimal.valueOf(request.years())))
                .setScale(2, RoundingMode.HALF_UP);

        List<Pillar3ProductComparison> comparisons = products.stream()
                .map(product -> project(product, request))
                .sorted(Comparator.comparing(Pillar3ProductComparison::finalCapital).reversed())
                .toList();

        return new Pillar3CompareResponse(totalPaidIn, MAX_ANNUAL_CONTRIBUTION, comparisons);
    }

    /** Inactive products stay comparable so a stored selection keeps working after an admin deactivates one. */
    private List<Pillar3Product> resolveProducts(List<UUID> productIds) {
        Map<UUID, Pillar3Product> productsById = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Pillar3Product::getId, Function.identity()));

        List<UUID> missing = productIds.stream()
                .distinct()
                .filter(id -> !productsById.containsKey(id))
                .toList();
        if (!missing.isEmpty()) {
            throw ResourceNotFoundException.of("Pillar3Product",
                    missing.stream().map(UUID::toString).collect(Collectors.joining(", ")));
        }

        return productIds.stream().distinct().map(productsById::get).toList();
    }

    private Pillar3ProductComparison project(Pillar3Product product, Pillar3CompareRequest request) {
        BigDecimal grossRate = Pillar3ReturnModel.grossRate(product.getEquityPct());
        BigDecimal feeRate = product.getTerPct().movePointLeft(2);
        BigDecimal contribution = request.annualContribution();

        BigDecimal balance = request.currentBalance();
        BigDecimal totalFees = BigDecimal.ZERO;

        for (int i = 1; i <= request.years(); i++) {
            balance = balance.multiply(BigDecimal.ONE.add(grossRate))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal fee = balance.multiply(feeRate)
                    .setScale(2, RoundingMode.HALF_UP);
            balance = balance.subtract(fee).add(contribution);
            totalFees = totalFees.add(fee);
        }

        return new Pillar3ProductComparison(
                product.getId(),
                product.getProvider(),
                product.getName(),
                product.getIsin(),
                product.getEquityPct(),
                product.getTerPct(),
                Pillar3ReturnModel.grossReturnPct(product.getEquityPct()),
                Pillar3ReturnModel.netReturnPct(product.getEquityPct(), product.getTerPct()),
                totalFees,
                balance
        );
    }
}
