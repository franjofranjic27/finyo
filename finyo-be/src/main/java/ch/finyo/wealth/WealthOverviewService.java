package ch.finyo.wealth;

import ch.finyo.common.SwissTime;
import ch.finyo.investment.AssetClass;
import ch.finyo.investment.PortfolioService;
import ch.finyo.pillar3.Pillar3ScenarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Builds the aggregated net-worth overview across all wealth buckets and the
 * snapshot-based history/year-to-date figures.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WealthOverviewService {

    private static final int PERCENT_SCALE = 2;
    private static final int MONEY_SCALE = 4;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final WealthBucketRepository bucketRepository;
    private final NetWorthSnapshotRepository snapshotRepository;
    private final PortfolioService portfolioService;
    private final Pillar3ScenarioService pillar3ScenarioService;

    /**
     * Resolves all bucket balances (MANUAL from the stored balance, PORTFOLIO
     * live from the investment portfolio, PILLAR3 from the default pillar 3a
     * scenario) and upserts today's net-worth snapshot so the history grows
     * over time.
     *
     * Deliberately NOT transactional: {@link PortfolioService#getPortfolio}
     * performs live SIX price fetches that must not run inside a database
     * transaction, and the snapshot upsert is a single atomic ON CONFLICT
     * statement in its own repository-level transaction.
     */
    public WealthOverviewResponse getOverview(String userId) {
        log.debug("Building net-worth overview for user={}", userId);
        List<WealthBucket> buckets = bucketRepository.findByUserIdOrderBySortOrderAscNameAsc(userId);
        OffsetDateTime asOf = OffsetDateTime.now(ZoneOffset.UTC);
        LocalDate today = LocalDate.now(SwissTime.ZONE);

        if (buckets.isEmpty()) {
            return new WealthOverviewResponse(List.of(),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, asOf);
        }

        Map<AssetClass, BigDecimal> portfolioValues = loadPortfolioValues(userId, buckets);
        BigDecimal pillar3Balance = loadPillar3Balance(userId, buckets);
        int remainingMonths = monthsRemaining(today);

        List<ResolvedBucket> resolved = buckets.stream()
                .map(bucket -> new ResolvedBucket(bucket,
                        resolveBalance(bucket, portfolioValues, pillar3Balance)))
                .toList();
        BigDecimal total = resolved.stream()
                .map(ResolvedBucket::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<WealthBucketOverviewResponse> rows = resolved.stream()
                .map(r -> toRow(r.bucket(), r.balance(), total, remainingMonths))
                .toList();
        BigDecimal totalMonthlyRate = sum(rows, WealthBucketOverviewResponse::monthlyRate);
        BigDecimal totalForecastYearEnd = sum(rows, WealthBucketOverviewResponse::forecastYearEnd);

        NetWorthSnapshot baseline = findYtdBaseline(userId, today);
        BigDecimal ytdChange = baseline != null ? total.subtract(baseline.getTotal()) : null;
        BigDecimal ytdChangePct = baseline != null ? percentOf(ytdChange, baseline.getTotal()) : null;

        snapshotRepository.upsert(userId, today, total.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        return new WealthOverviewResponse(rows, total, totalMonthlyRate, totalForecastYearEnd,
                ytdChange, ytdChangePct, asOf);
    }

    @Transactional(readOnly = true)
    public NetWorthHistoryResponse getHistory(String userId, int months) {
        log.debug("Fetching net-worth history for user={} months={}", userId, months);
        LocalDate from = LocalDate.now(SwissTime.ZONE).minusMonths(months);
        List<NetWorthHistoryPoint> points = snapshotRepository
                .findByUserIdAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(userId, from)
                .stream()
                .map(snapshot -> new NetWorthHistoryPoint(snapshot.getSnapshotDate(), snapshot.getTotal()))
                .toList();
        return new NetWorthHistoryResponse(points);
    }

    /** One portfolio read per overview request, and only when a PORTFOLIO bucket exists. */
    private Map<AssetClass, BigDecimal> loadPortfolioValues(String userId, List<WealthBucket> buckets) {
        boolean hasPortfolioBucket = buckets.stream()
                .anyMatch(bucket -> bucket.getSource() == WealthSource.PORTFOLIO);
        if (!hasPortfolioBucket) {
            return Map.of();
        }
        Map<AssetClass, BigDecimal> values = new EnumMap<>(AssetClass.class);
        portfolioService.getPortfolio(userId).positions()
                .forEach(position -> values.merge(position.assetClass(), position.value(), BigDecimal::add));
        return values;
    }

    /** One scenario read per overview request, and only when a PILLAR3 bucket exists. */
    private BigDecimal loadPillar3Balance(String userId, List<WealthBucket> buckets) {
        boolean hasPillar3Bucket = buckets.stream()
                .anyMatch(bucket -> bucket.getSource() == WealthSource.PILLAR3);
        if (!hasPillar3Bucket) {
            return BigDecimal.ZERO;
        }
        return pillar3ScenarioService.getDefaultCurrentBalance(userId)
                .orElse(BigDecimal.ZERO);
    }

    private static BigDecimal resolveBalance(WealthBucket bucket, Map<AssetClass, BigDecimal> portfolioValues,
                                             BigDecimal pillar3Balance) {
        return switch (bucket.getSource()) {
            case MANUAL -> bucket.getManualBalance() != null ? bucket.getManualBalance() : BigDecimal.ZERO;
            case PILLAR3 -> pillar3Balance;
            case PORTFOLIO -> bucket.assetClassList().stream()
                    .map(assetClass -> portfolioValues.getOrDefault(assetClass, BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        };
    }

    private static WealthBucketOverviewResponse toRow(WealthBucket bucket, BigDecimal balance,
                                                      BigDecimal total, int remainingMonths) {
        BigDecimal forecastYearEnd = balance.add(
                bucket.getMonthlyRate().multiply(BigDecimal.valueOf(remainingMonths)));
        return new WealthBucketOverviewResponse(
                bucket.getId(),
                bucket.getName(),
                bucket.getNote(),
                bucket.getSource(),
                bucket.assetClassList(),
                balance,
                percentOf(balance, total),
                bucket.getMonthlyRate(),
                forecastYearEnd,
                bucket.getSortOrder());
    }

    /** Earliest snapshot of the current year strictly before today; null when none exists. */
    private @Nullable NetWorthSnapshot findYtdBaseline(String userId, LocalDate today) {
        LocalDate startOfYear = LocalDate.of(today.getYear(), Month.JANUARY, 1);
        return snapshotRepository
                .findFirstByUserIdAndSnapshotDateGreaterThanEqualAndSnapshotDateLessThanOrderBySnapshotDateAsc(
                        userId, startOfYear, today)
                .orElse(null);
    }

    /** Full months left until year end: July → 5, December → 0. */
    static int monthsRemaining(LocalDate date) {
        return 12 - date.getMonthValue();
    }

    private static BigDecimal sum(List<WealthBucketOverviewResponse> rows,
                                  Function<WealthBucketOverviewResponse, BigDecimal> amount) {
        return rows.stream().map(amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** part / base * 100 at scale 2 HALF_UP; zero when the base is zero. */
    private static BigDecimal percentOf(BigDecimal part, BigDecimal base) {
        if (base.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return part.multiply(HUNDRED).divide(base, PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private record ResolvedBucket(WealthBucket bucket, BigDecimal balance) {}
}
