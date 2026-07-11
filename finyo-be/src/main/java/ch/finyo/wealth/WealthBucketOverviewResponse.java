package ch.finyo.wealth;

import ch.finyo.investment.AssetClass;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** A wealth bucket with its resolved balance and derived overview figures. */
public record WealthBucketOverviewResponse(
        UUID id,
        String name,
        String note,
        WealthSource source,
        List<AssetClass> assetClasses,
        BigDecimal balance,
        BigDecimal sharePct,
        BigDecimal monthlyRate,
        BigDecimal forecastYearEnd,
        int sortOrder
) {}
