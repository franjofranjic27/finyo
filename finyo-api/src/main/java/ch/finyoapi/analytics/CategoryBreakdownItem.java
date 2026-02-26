package ch.finyoapi.analytics;

import java.math.BigDecimal;
import java.util.UUID;

public record CategoryBreakdownItem(
        UUID categoryId,
        String categoryName,
        String categoryColor,
        BigDecimal total,
        double percentage
) {}
