package ch.finyo.pillar3;

import ch.finyo.tax.TaxCivilStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record Pillar3ScenarioInputs(
        BigDecimal currentBalance,
        BigDecimal annualContribution,
        BigDecimal assumedAnnualReturnPercent,
        int yearsToRetirement,
        BigDecimal grossEmploymentIncome,
        TaxCivilStatus civilStatus,
        String cantonCode,
        Integer taxYear,
        UUID productId
) {
    public static Pillar3ScenarioInputs from(Pillar3Scenario scenario) {
        return new Pillar3ScenarioInputs(
                scenario.getCurrentBalance(),
                scenario.getAnnualContribution(),
                scenario.getAssumedAnnualReturnPercent(),
                scenario.getYearsToRetirement(),
                scenario.getGrossEmploymentIncome(),
                scenario.getCivilStatus(),
                scenario.getCantonCode(),
                scenario.getTaxYear(),
                scenario.getProductId()
        );
    }
}
