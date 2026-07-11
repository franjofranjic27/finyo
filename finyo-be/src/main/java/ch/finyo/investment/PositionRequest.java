package ch.finyo.investment;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request to add a portfolio position. Presence rules (at least one of
 * name/isin/valor, non-null quantity/purchasePrice) are validated in the
 * service so bulk imports — which bypass Bean Validation — can report them
 * per row. {@code currentPrice} is only applied as a manual price override
 * when no market price is available for the instrument.
 */
public record PositionRequest(
        @Size(max = 200) String name,
        @Size(max = 12) String isin,
        @Size(max = 20) String valor,
        @Positive BigDecimal quantity,
        @PositiveOrZero BigDecimal purchasePrice,
        @PositiveOrZero BigDecimal currentPrice
) {}
