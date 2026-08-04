package ch.finyo.wealth;

import ch.finyo.investment.AssetClass;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;

/**
 * A wealth row with its resolved balance and derived overview figures.
 *
 * <p>The id is a string rather than a UUID because the overview mixes two kinds
 * of rows: persisted manual buckets (UUID as string) and the auto-mirrored rows
 * synthesized from the portfolio and the default pillar 3a scenario, which
 * carry the stable synthetic ids {@code auto-portfolio} / {@code auto-pillar3}
 * and are flagged with {@code auto = true}. Auto rows are read-only and have a
 * null {@code monthlyRate} when no deposit can be derived.
 */
public record WealthBucketOverviewResponse(
        String id,
        String name,
        @Nullable String note,
        WealthSource source,
        List<AssetClass> assetClasses,
        BigDecimal balance,
        BigDecimal sharePct,
        @Nullable BigDecimal monthlyRate,
        BigDecimal forecastYearEnd,
        int sortOrder,
        boolean auto
) {}
