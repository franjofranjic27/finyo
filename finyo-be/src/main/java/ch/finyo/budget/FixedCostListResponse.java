package ch.finyo.budget;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Function;

public record FixedCostListResponse(
        List<FixedCostResponse> items,
        BigDecimal totalPerYear,
        BigDecimal totalPerMonth
) {
    public static FixedCostListResponse of(List<FixedCostResponse> items) {
        return new FixedCostListResponse(
                items,
                sum(items, FixedCostResponse::amountPerYear),
                sum(items, FixedCostResponse::amountPerMonth)
        );
    }

    // totals are the sum of the already-rounded item values so that the list
    // always adds up to its own total in the UI
    private static BigDecimal sum(List<FixedCostResponse> items, Function<FixedCostResponse, BigDecimal> value) {
        return items.stream()
                .map(value)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
