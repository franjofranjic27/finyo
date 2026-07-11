package ch.finyo.budget;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record FixedCostResponse(
        UUID id,
        String name,
        String category,
        PaymentInterval paymentInterval,
        BigDecimal amount,
        BigDecimal amountPerYear,
        BigDecimal amountPerMonth
) {
    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);
    private static final int DERIVED_SCALE = 2;

    public static FixedCostResponse from(FixedCost fixedCost) {
        BigDecimal amountPerYear = yearlyAmountOf(fixedCost).setScale(DERIVED_SCALE, RoundingMode.HALF_UP);
        BigDecimal amountPerMonth = amountPerYear.divide(MONTHS_PER_YEAR, DERIVED_SCALE, RoundingMode.HALF_UP);
        return new FixedCostResponse(
                fixedCost.getId(),
                fixedCost.getName(),
                fixedCost.getCategory(),
                fixedCost.getPaymentInterval(),
                fixedCost.getAmount(),
                amountPerYear,
                amountPerMonth
        );
    }

    private static BigDecimal yearlyAmountOf(FixedCost fixedCost) {
        return fixedCost.getPaymentInterval() == PaymentInterval.MONTHLY
                ? fixedCost.getAmount().multiply(MONTHS_PER_YEAR)
                : fixedCost.getAmount();
    }
}
