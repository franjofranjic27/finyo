package ch.finyo.wealth;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NetWorthHistoryPoint(
        LocalDate date,
        BigDecimal total
) {}
