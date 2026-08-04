package ch.finyo.wealth;

import ch.finyo.investment.AssetClass;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request to create or update a wealth bucket. Only MANUAL buckets can be
 * saved (portfolio and pillar 3a rows are synthesized in the overview); the
 * service validates the source, the required manualBalance and that no asset
 * classes are sent, producing precise error messages for retired clients.
 */
public record WealthBucketRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 200) String note,
        @NotNull WealthSource source,
        @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal manualBalance,
        List<AssetClass> assetClasses,
        @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal monthlyRate,
        Integer sortOrder
) {}
