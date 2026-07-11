package ch.finyo.investment;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PortfolioHistoryPoint(
        LocalDate date,
        BigDecimal totalValue,
        BigDecimal totalCost
) {}
