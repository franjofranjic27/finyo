package ch.finyo.investment;

import ch.finyo.fx.FxRateType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Detail view of a single portfolio position, including instrument master
 * data, the priced valuation and the share of the total portfolio value.
 *
 * <p>{@code value} is in the position's own {@code currency}; {@code valueChf} is that value in
 * CHF, and the CHF-denominated figures (valueChf, gainLoss, returnPercent, portfolioShare, the fx
 * fields) are null when the position could not be converted — see {@link PortfolioPositionResponse}.
 */
public record PositionDetailResponse(
        UUID positionId,
        UUID instrumentId,
        String name,
        String isin,
        String valor,
        AssetClass assetClass,
        BigDecimal ter,
        String currency,
        BigDecimal quantity,
        BigDecimal avgPurchasePrice,
        LocalDate purchaseDate,
        BigDecimal currentPrice,
        PriceSource priceSource,
        // The trading day the price belongs to, not the day we fetched it — a Friday close read
        // on a Sunday is three days old, and saying "updated just now" would be a small lie told
        // very often. Replaces the old priceUpdatedAt, which reported the fetch time.
        LocalDate priceAsOf,
        boolean stale,
        BigDecimal value,
        BigDecimal valueChf,
        BigDecimal gainLoss,
        BigDecimal returnPercent,
        BigDecimal portfolioShare,
        BigDecimal fxRate,
        LocalDate fxRateDate,
        FxRateType fxRateType,
        String factsheetUrl,
        FactsheetInfo factsheet
) {
    /** Metadata of the stored factsheet PDF; null when none is uploaded. */
    public record FactsheetInfo(
            String filename,
            Long sizeBytes,
            OffsetDateTime uploadedAt
    ) {
        static FactsheetInfo from(InstrumentFactsheetRepository.FactsheetMetadata metadata) {
            return new FactsheetInfo(metadata.getFilename(), metadata.getSize(), metadata.getUploadedAt());
        }
    }
}
