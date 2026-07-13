package ch.finyo.investment;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Partial update of a holding (the position itself, not its instrument).
 *
 * Patch semantics: quantity and purchasePrice are applied only when present —
 * they map to NOT NULL columns and can never be cleared. purchaseDate is
 * always applied — the edit dialog sends the full field set and uses null as
 * an explicit clear. currentPrice is applied only when present and overwrites
 * the manual price on the position's instrument.
 *
 * Because a record cannot distinguish an absent purchaseDate from an explicit
 * null, a patch where all four fields are null is rejected as empty in
 * {@link PositionDetailService}; clearing the purchase date therefore
 * requires sending at least one other field.
 */
public record PositionPatchRequest(
        @Positive BigDecimal quantity,
        @PositiveOrZero BigDecimal purchasePrice,
        LocalDate purchaseDate,
        @PositiveOrZero BigDecimal currentPrice
) {}
