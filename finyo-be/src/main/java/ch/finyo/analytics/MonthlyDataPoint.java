package ch.finyo.analytics;

import java.math.BigDecimal;

public record MonthlyDataPoint(
        int year,
        int month,
        BigDecimal expenses,
        BigDecimal income,
        BigDecimal net
) {}
