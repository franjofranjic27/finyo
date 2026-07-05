package ch.finyo.tax;

import java.math.BigDecimal;

public record TaxYearInputs(
        String cantonCode,
        Integer bfsNumber,
        TaxCivilStatus civilStatus,
        Integer numberOfChildren,
        ChurchAffiliation churchAffiliation,
        BigDecimal grossEmploymentIncome,
        BigDecimal selfEmploymentIncome,
        BigDecimal investmentIncome,
        BigDecimal rentalIncome,
        BigDecimal deductionProfessionalExpenses,
        BigDecimal deductionInsurancePremiums,
        BigDecimal deductionCharitableDonations,
        BigDecimal deductionDebtInterest,
        BigDecimal pillar3aContribution,
        BigDecimal netWealth
) {
    public static TaxYearInputs from(TaxYear taxYear) {
        return new TaxYearInputs(
                taxYear.getCantonCode(),
                taxYear.getBfsNumber(),
                taxYear.getCivilStatus(),
                taxYear.getNumberOfChildren(),
                taxYear.getChurchAffiliation(),
                taxYear.getGrossEmploymentIncome(),
                taxYear.getSelfEmploymentIncome(),
                taxYear.getInvestmentIncome(),
                taxYear.getRentalIncome(),
                taxYear.getDeductionProfessionalExpenses(),
                taxYear.getDeductionInsurancePremiums(),
                taxYear.getDeductionCharitableDonations(),
                taxYear.getDeductionDebtInterest(),
                taxYear.getPillar3aContribution(),
                taxYear.getNetWealth()
        );
    }
}
