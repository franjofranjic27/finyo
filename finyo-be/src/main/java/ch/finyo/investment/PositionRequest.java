package ch.finyo.investment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request to add a portfolio position. At least one of name/isin/valor must
 * be provided (validated in the service so bulk imports can report the rule
 * per row). {@code currentPrice} is only applied as a manual price override
 * when no market price is available for the instrument.
 */
public record PositionRequest(
        @Size(max = 200) String name,
        @Size(max = 12) String isin,
        @Size(max = 20) String valor,
        @NotNull @Positive BigDecimal quantity,
        @NotNull @PositiveOrZero BigDecimal purchasePrice,
        @PositiveOrZero BigDecimal currentPrice
) {}
