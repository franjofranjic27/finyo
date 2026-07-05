package ch.finyo.tax;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TaxInputRequest(
        @NotNull @Min(2020) @Max(2030) int taxYear,
        @NotBlank String cantonCode,
        Integer bfsNumber,
        @NotNull TaxCivilStatus civilStatus,
        @Min(0) int numberOfChildren,
        ChurchAffiliation churchAffiliation,
        @NotNull @DecimalMin("0") BigDecimal grossEmploymentIncome,
        BigDecimal selfEmploymentIncome,
        BigDecimal investmentIncome,
        BigDecimal rentalIncome,
        BigDecimal deductionProfessionalExpenses,
        BigDecimal deductionInsurancePremiums,
        BigDecimal deductionCharitableDonations,
        BigDecimal deductionDebtInterest,
        BigDecimal pillar3aContribution,
        BigDecimal netWealth
) {}
