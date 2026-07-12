package ch.finyo.wealth;

import ch.finyo.investment.AssetClass;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Stored fields of a wealth bucket, returned by create/update. Computed values
 * (balance, share, forecast) come from the overview endpoint only.
 */
public record WealthBucketResponse(
        UUID id,
        String name,
        String note,
        WealthSource source,
        List<AssetClass> assetClasses,
        BigDecimal manualBalance,
        BigDecimal monthlyRate,
        int sortOrder
) {
    public static WealthBucketResponse from(WealthBucket bucket) {
        return new WealthBucketResponse(
                bucket.getId(),
                bucket.getName(),
                bucket.getNote(),
                bucket.getSource(),
                bucket.assetClassList(),
                bucket.getManualBalance(),
                bucket.getMonthlyRate(),
                bucket.getSortOrder());
    }
}
