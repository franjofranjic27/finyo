package ch.finyo.transaction;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One booked statement line extracted from a camt.053 document. The amount is
 * already signed: debits are negative, credits positive, reversals flipped.
 */
public record CamtEntry(
        LocalDate date,
        BigDecimal amount,
        @Nullable String currency,
        @Nullable String description,
        @Nullable String counterparty,
        @Nullable String externalRef
) {}
