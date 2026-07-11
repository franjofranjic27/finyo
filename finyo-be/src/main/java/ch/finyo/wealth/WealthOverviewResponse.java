package ch.finyo.wealth;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Aggregated net-worth overview. {@code ytdChange}/{@code ytdChangePct} compare
 * the current total against the earliest snapshot of the current year and are
 * null when no prior snapshot exists.
 */
public record WealthOverviewResponse(
        List<WealthBucketOverviewResponse> buckets,
        BigDecimal total,
        BigDecimal totalMonthlyRate,
        BigDecimal totalForecastYearEnd,
        @Nullable BigDecimal ytdChange,
        @Nullable BigDecimal ytdChangePct,
        OffsetDateTime asOf
) {}
