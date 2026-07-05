package ch.finyo.tax;

import java.math.BigDecimal;
import java.util.List;

public record TaxResultResponse(
        int taxYear,
        String cantonCode,
        String communeName,
        TaxCivilStatus civilStatus,

        // Income calculation
        BigDecimal grossIncome,
        BigDecimal totalDeductions,
        BigDecimal taxableIncome,

        // Tax amounts
        BigDecimal federalTax,
        BigDecimal cantonalTax,
        BigDecimal communalTax,
        BigDecimal churchTax,
        BigDecimal totalIncomeTax,

        // Rates
        Double effectiveRatePercent,
        Double marginalRatePercent,

        // Wealth tax
        BigDecimal wealthTax,
        BigDecimal grandTotal,

        // Pillar 3a impact
        BigDecimal pillar3aTaxSaving,

        // Chart data
        List<TaxBreakdownItem> breakdown
) {}
