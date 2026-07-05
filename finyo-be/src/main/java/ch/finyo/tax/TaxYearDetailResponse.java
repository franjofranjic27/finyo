package ch.finyo.tax;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TaxYearDetailResponse(
        int year,
        TaxYearStatus status,
        TaxYearInputs inputs,
        TaxResultResponse calculation,
        List<TaxPaymentResponse> payments,
        List<TaxDeadlineResponse> deadlines,
        BigDecimal expectedTax,
        BigDecimal paidTotal,
        BigDecimal openAmount,
        LocalDate filingDeadline,
        LocalDate filedAt,
        LocalDate assessedAt,
        BigDecimal assessedAmount
) {}
