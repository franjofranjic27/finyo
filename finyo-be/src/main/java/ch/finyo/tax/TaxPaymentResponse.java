package ch.finyo.tax;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TaxPaymentResponse(
        UUID id,
        LocalDate paymentDate,
        BigDecimal amount,
        String label
) {
    public static TaxPaymentResponse from(TaxPayment payment) {
        return new TaxPaymentResponse(
                payment.getId(),
                payment.getPaymentDate(),
                payment.getAmount(),
                payment.getLabel()
        );
    }
}
