package ch.finyo.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SpendingSummaryResponse(
        BigDecimal totalExpenses,
        BigDecimal totalIncome,
        BigDecimal netAmount,
        LocalDate rangeStart,
        LocalDate rangeEnd,
        long transactionCount
) {}
