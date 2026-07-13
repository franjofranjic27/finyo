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
 * Request to create or update a wealth bucket. Source-dependent presence rules
 * (manualBalance required iff MANUAL, assetClasses required non-empty iff
 * PORTFOLIO, both forbidden for PILLAR3) are validated in the service to
 * produce precise error messages.
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
