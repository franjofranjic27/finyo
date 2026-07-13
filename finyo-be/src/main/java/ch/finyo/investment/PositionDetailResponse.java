package ch.finyo.investment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Detail view of a single portfolio position, including instrument master
 * data, the priced valuation and the share of the total portfolio value.
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
        OffsetDateTime priceUpdatedAt,
        BigDecimal value,
        BigDecimal gainLoss,
        BigDecimal returnPercent,
        BigDecimal portfolioShare,
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
