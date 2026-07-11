package ch.finyo.pillar3;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record Pillar3CompareRequest(
        @NotNull @DecimalMin("0") BigDecimal currentBalance,
        @NotNull @DecimalMin("0") @DecimalMax("7258") BigDecimal annualContribution,
        @Min(1) @Max(50) int years,
        @NotEmpty @Size(max = 10) List<UUID> productIds
) {}
