package ch.finyo.tax;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TaxYearRequest(
        @Size(max = 2) String cantonCode,
        Integer bfsNumber,
        TaxCivilStatus civilStatus,
        @Min(0) Integer numberOfChildren,
        ChurchAffiliation churchAffiliation,
        @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal grossEmploymentIncome,
        @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal selfEmploymentIncome,
        @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal investmentIncome,
        @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal rentalIncome,
        @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal deductionProfessionalExpenses,
        @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal deductionInsurancePremiums,
        @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal deductionCharitableDonations,
        @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal deductionDebtInterest,
        @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal pillar3aContribution,
        @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal netWealth,
        LocalDate filingDeadline,
        @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal assessedAmount
) {}
