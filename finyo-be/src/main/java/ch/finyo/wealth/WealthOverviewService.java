package ch.finyo.wealth;

import ch.finyo.common.SwissTime;
import ch.finyo.investment.PortfolioResponse;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds the aggregated net-worth overview and the snapshot-based
 * history/year-to-date figures.
 *
 * <p>The overview consists of up to two auto-mirrored read-only rows (the whole
 * investment portfolio and the default pillar 3a scenario, synthesized live and
 * never persisted) followed by the user's manual buckets. Totals, shares and
 * the daily snapshot include the auto rows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WealthOverviewService {

    /** Stable synthetic ids of the auto rows — deliberately not valid UUIDs. */
    static final String AUTO_PORTFOLIO_ID = "auto-portfolio";
    static final String AUTO_PILLAR3_ID = "auto-pillar3";

    // Default display names; the frontend localizes auto rows by source.
    private static final String AUTO_PORTFOLIO_NAME = "Portfolio";
    private static final String AUTO_PILLAR3_NAME = "Säule 3a";

    private static final int PERCENT_SCALE = 2;
    private static final int MONEY_SCALE = 4;
    private static final int RATE_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

    private final WealthBucketRepository bucketRepository;
    private final NetWorthSnapshotRepository snapshotRepository;
    private final PortfolioService portfolioService;
    private final Pillar3ScenarioService pillar3ScenarioService;

    /**
     * Synthesizes the auto rows, appends the manual buckets, computes shares and
     * year-end forecasts and upserts today's net-worth snapshot so the history
     * grows over time.
     *
     * Deliberately NOT transactional: {@link PortfolioService#getPortfolio}
     * performs live SIX price fetches that must not run inside a database
     * transaction, and the snapshot upsert is a single atomic ON CONFLICT
     * statement in its own repository-level transaction.
     */
    public WealthOverviewResponse getOverview(String userId) {
        log.debug("Building net-worth overview for user={}", userId);
        OffsetDateTime asOf = OffsetDateTime.now(ZoneOffset.UTC);
        LocalDate today = LocalDate.now(SwissTime.ZONE);

        List<ResolvedRow> rows = new ArrayList<>();
        portfolioRow(userId).ifPresent(rows::add);
        pillar3Row(userId).ifPresent(rows::add);
        bucketRepository.findByUserIdOrderBySortOrderAscNameAsc(userId).stream()
                .map(WealthOverviewService::manualRow)
                .forEach(rows::add);

        if (rows.isEmpty()) {
            return new WealthOverviewResponse(List.of(),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, asOf);
        }

        BigDecimal total = rows.stream()
                .map(ResolvedRow::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int remainingMonths = monthsRemaining(today);
        List<WealthBucketOverviewResponse> bucketRows = rows.stream()
                .map(row -> toRow(row, total, remainingMonths))
                .toList();
        BigDecimal totalMonthlyRate = bucketRows.stream()
                .map(WealthBucketOverviewResponse::monthlyRate)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalForecastYearEnd = bucketRows.stream()
                .map(WealthBucketOverviewResponse::forecastYearEnd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        NetWorthSnapshot baseline = findYtdBaseline(userId, today);
        BigDecimal ytdChange = baseline != null ? total.subtract(baseline.getTotal()) : null;
        BigDecimal ytdChangePct = baseline != null ? percentOf(ytdChange, baseline.getTotal()) : null;

        snapshotRepository.upsert(userId, today, total.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        return new WealthOverviewResponse(bucketRows, total, totalMonthlyRate, totalForecastYearEnd,
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

    /**
     * One row for the whole portfolio, present as soon as the user holds a
     * position. {@code totalValue} is the CHF total with unconvertible foreign
     * positions excluded — the same figure the investments page shows, so the
     * two modules never disagree.
     */
    private Optional<ResolvedRow> portfolioRow(String userId) {
        PortfolioResponse portfolio = portfolioService.getPortfolio(userId);
        if (portfolio.positions().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedRow(AUTO_PORTFOLIO_ID, AUTO_PORTFOLIO_NAME, null,
                WealthSource.PORTFOLIO, portfolio.totalValue(), null, -2, true));
    }

    /**
     * One row for the default pillar 3a scenario. The monthly rate is derived
     * from the scenario's annual contribution so the year-end forecast stays
     * meaningful; it is null when nothing is contributed.
     */
    private Optional<ResolvedRow> pillar3Row(String userId) {
        return pillar3ScenarioService.getDefaultScenarioSummary(userId)
                .map(summary -> new ResolvedRow(AUTO_PILLAR3_ID, AUTO_PILLAR3_NAME, null,
                        WealthSource.PILLAR3, summary.currentBalance(),
                        monthlyRateOf(summary.annualContribution()), -1, true));
    }

    private static ResolvedRow manualRow(WealthBucket bucket) {
        BigDecimal balance = bucket.getManualBalance() != null ? bucket.getManualBalance() : BigDecimal.ZERO;
        return new ResolvedRow(bucket.getId().toString(), bucket.getName(), bucket.getNote(),
                WealthSource.MANUAL, balance, bucket.getMonthlyRate(), bucket.getSortOrder(), false);
    }

    private static @Nullable BigDecimal monthlyRateOf(@Nullable BigDecimal annualContribution) {
        if (annualContribution == null || annualContribution.signum() <= 0) {
            return null;
        }
        return annualContribution.divide(MONTHS_PER_YEAR, RATE_SCALE, RoundingMode.HALF_UP);
    }

    private static WealthBucketOverviewResponse toRow(ResolvedRow row, BigDecimal total,
                                                      int remainingMonths) {
        BigDecimal forecastYearEnd = row.monthlyRate() != null
                ? row.balance().add(row.monthlyRate().multiply(BigDecimal.valueOf(remainingMonths)))
                : row.balance();
        return new WealthBucketOverviewResponse(
                row.id(),
                row.name(),
                row.note(),
                row.source(),
                List.of(),
                row.balance(),
                percentOf(row.balance(), total),
                row.monthlyRate(),
                forecastYearEnd,
                row.sortOrder(),
                row.auto());
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

    /** part / base * 100 at scale 2 HALF_UP; zero when the base is zero. */
    private static BigDecimal percentOf(BigDecimal part, BigDecimal base) {
        if (base.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return part.multiply(HUNDRED).divide(base, PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * A row before share/forecast math. Auto rows come first with negative sort
     * orders (-2 portfolio, -1 pillar 3a) so ordering stays self-describing
     * next to the manual buckets that start at 0.
     */
    private record ResolvedRow(String id, String name, @Nullable String note, WealthSource source,
                               BigDecimal balance, @Nullable BigDecimal monthlyRate,
                               int sortOrder, boolean auto) {}
}
