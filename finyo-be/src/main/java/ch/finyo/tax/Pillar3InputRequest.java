package ch.finyo.tax;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record Pillar3InputRequest(
        @NotNull @DecimalMin("0") BigDecimal currentBalance,
        @NotNull @DecimalMin("0") BigDecimal annualContribution,
        @DecimalMin("0") @DecimalMax("20") double assumedAnnualReturnPercent,
        @NotNull @Min(1) @Max(50) int yearsToRetirement,
        // Optional — required for tax-saving calculation
        BigDecimal grossEmploymentIncome,
        TaxCivilStatus civilStatus,
        String cantonCode,
        int taxYear
) {}
