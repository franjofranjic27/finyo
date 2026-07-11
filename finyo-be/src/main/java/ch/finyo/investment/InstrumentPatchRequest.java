package ch.finyo.investment;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Partial update of the instrument behind a position: only non-null fields
 * are applied. Blank-name and factsheet-URL-scheme rules are enforced in
 * {@link PositionDetailService} because Bean Validation cannot express
 * "constraint only when present" for them.
 */
public record InstrumentPatchRequest(
        @Size(max = 255) String name,
        AssetClass assetClass,
        @Size(max = 20) String valor,
        @DecimalMin(value = "0", message = "ter must be between 0 and 10")
        @DecimalMax(value = "10", message = "ter must be between 0 and 10")
        BigDecimal ter,
        @Size(max = 500) String factsheetUrl
) {}
