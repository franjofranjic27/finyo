package ch.finyo.investment;

import java.util.List;

public record PortfolioHistoryResponse(
        List<PortfolioHistoryPoint> points
) {}
