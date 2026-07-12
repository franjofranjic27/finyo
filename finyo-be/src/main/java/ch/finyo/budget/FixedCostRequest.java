package ch.finyo.budget;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record FixedCostRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 50) String category,
        @NotNull PaymentInterval paymentInterval,
        @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal amount
) {}
