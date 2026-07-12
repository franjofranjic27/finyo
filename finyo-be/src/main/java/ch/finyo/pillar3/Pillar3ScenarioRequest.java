package ch.finyo.pillar3;

import ch.finyo.tax.TaxCivilStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record Pillar3ScenarioRequest(
        @NotBlank @Size(max = 100) String name,
        Boolean isDefault,
        @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal currentBalance,
        @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal annualContribution,
        @DecimalMin("0") @DecimalMax("20") double assumedAnnualReturnPercent,
        @Min(1) @Max(50) int yearsToRetirement,
        // Optional — required for the tax-saving part of the calculation
        @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal grossEmploymentIncome,
        TaxCivilStatus civilStatus,
        @Size(max = 2) String cantonCode,
        Integer taxYear,
        UUID productId
) {
    public Pillar3ScenarioRequest {
        isDefault = isDefault != null && isDefault;
    }
}
