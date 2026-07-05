package ch.finyo.tax;

import java.math.BigDecimal;

public record TaxBreakdownItem(
        String label,
        BigDecimal amount,
        Double percentage
) {}
