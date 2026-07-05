package ch.finyo.tax;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TaxPaymentRequest(
        @NotNull LocalDate paymentDate,
        @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @Size(max = 100) String label
) {}
