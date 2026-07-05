package ch.finyo.tax;

import java.math.BigDecimal;

public record TaxYearSummaryResponse(
        int year,
        TaxYearStatus status,
        BigDecimal expectedTax,
        BigDecimal paidTotal,
        BigDecimal openAmount,
        BigDecimal grossIncome,
        Double effectiveRatePercent
) {}
